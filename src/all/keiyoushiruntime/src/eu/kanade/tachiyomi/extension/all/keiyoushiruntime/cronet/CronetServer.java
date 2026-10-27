package eu.kanade.tachiyomi.extension.all.keiyoushiruntime.cronet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.CipherSuite;
import okhttp3.Handshake;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.TlsVersion;
import okio.Buffer;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

/** OkHttp's network call, over Cronet: sends one request as given, follows no redirects. */
final class CronetServer implements Interceptor {
  private static final ExecutorService EXECUTOR =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "KeiCronet");
            t.setDaemon(true);
            return t;
          });

  // Set by Cronet itself; it also sends its own Accept-Encoding and decodes the body
  private static final Set<String> CRONET_HEADERS =
      new HashSet<>(
          Arrays.asList(
              "accept-encoding",
              "connection",
              "content-length",
              "host",
              "keep-alive",
              "proxy-connection",
              "transfer-encoding",
              "te",
              "upgrade"));

  private static final Set<String> DECODED =
      new HashSet<>(Arrays.asList("gzip", "x-gzip", "deflate", "br"));

  private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

  // OkHttp's cache writes a handshake for every HTTPS response; Cronet doesn't report one
  private static final Handshake HANDSHAKE =
      Handshake.get(
          TlsVersion.TLS_1_3,
          CipherSuite.TLS_AES_128_GCM_SHA256,
          Collections.emptyList(),
          Collections.emptyList());

  private final CronetEngine engine;

  CronetServer(CronetEngine engine) {
    this.engine = engine;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    CompletableFuture<Response.Builder> done = new CompletableFuture<>();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    AtomicLong progress = new AtomicLong(System.nanoTime());
    long sentAt = System.currentTimeMillis();
    AtomicLong receivedAt = new AtomicLong();

    UrlRequest.Callback callback =
        new UrlRequest.Callback() {
          @Override
          public void onRedirectReceived(UrlRequest r, UrlResponseInfo info, String location) {
            done.complete(head(request, info, sentAt, System.currentTimeMillis()));
            r.cancel();
          }

          @Override
          public void onResponseStarted(UrlRequest r, UrlResponseInfo info) {
            receivedAt.set(System.currentTimeMillis());
            progress.set(System.nanoTime());
            r.read(ByteBuffer.allocateDirect(64 * 1024));
          }

          @Override
          public void onReadCompleted(UrlRequest r, UrlResponseInfo info, ByteBuffer buf) {
            progress.set(System.nanoTime());
            buf.flip();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            body.write(bytes, 0, bytes.length);
            buf.clear();
            r.read(buf);
          }

          @Override
          public void onSucceeded(UrlRequest r, UrlResponseInfo info) {
            done.complete(head(request, info, sentAt, receivedAt.get()));
          }

          @Override
          public void onFailed(UrlRequest r, UrlResponseInfo info, CronetException error) {
            done.completeExceptionally(error);
          }

          @Override
          public void onCanceled(UrlRequest r, UrlResponseInfo info) {
            done.completeExceptionally(new IOException("Canceled"));
          }
        };

    UrlRequest urlRequest;
    try {
      UrlRequest.Builder builder =
          engine
              .newUrlRequestBuilder(request.url().toString(), callback, EXECUTOR)
              .setHttpMethod(request.method());
      Headers headers = request.headers();
      for (int i = 0; i < headers.size(); i++) {
        if (!CRONET_HEADERS.contains(headers.name(i).toLowerCase(Locale.ROOT))) {
          builder.addHeader(headers.name(i), headers.value(i));
        }
      }
      RequestBody requestBody = request.body();
      if (requestBody != null) {
        Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        // Cronet refuses an upload without a Content-Type
        if (request.header("Content-Type") == null) {
          MediaType type = requestBody.contentType();
          builder.addHeader("Content-Type", (type != null ? type : OCTET_STREAM).toString());
        }
        builder.setUploadDataProvider(UploadDataProviders.create(buffer.readByteArray()), EXECUTOR);
      }
      urlRequest = builder.build();
    } catch (RuntimeException e) {
      throw new IOException(e);
    }
    urlRequest.start();

    Response.Builder head = await(chain, urlRequest, done, progress);
    String contentType = head.build().header("Content-Type");
    return head.body(
            ResponseBody.create(
                body.toByteArray(), contentType == null ? null : MediaType.parse(contentType)))
        .build();
  }

  /**
   * OkHttp's timeouts (connect, write and read until the response starts, then read between reads)
   * and cancellation
   */
  private static Response.Builder await(
      Chain chain,
      UrlRequest urlRequest,
      CompletableFuture<Response.Builder> done,
      AtomicLong progress)
      throws IOException {
    Call call = chain.call();
    long read = TimeUnit.MILLISECONDS.toNanos(chain.readTimeoutMillis());
    long beforeResponse =
        TimeUnit.MILLISECONDS.toNanos(
            (long) chain.connectTimeoutMillis()
                + chain.writeTimeoutMillis()
                + chain.readTimeoutMillis());
    long start = progress.get();
    try {
      while (true) {
        try {
          return done.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          if (call.isCanceled()) {
            urlRequest.cancel();
            throw new IOException("Canceled");
          }
          long last = progress.get();
          long timeout = last == start ? beforeResponse : read;
          if (read > 0 && System.nanoTime() - last > timeout) {
            urlRequest.cancel();
            throw new InterruptedIOException("timeout");
          }
        }
      }
    } catch (InterruptedException e) {
      urlRequest.cancel();
      Thread.currentThread().interrupt();
      throw new InterruptedIOException();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) throw (IOException) cause;
      throw new IOException(cause);
    }
  }

  private static Response.Builder head(
      Request request, UrlResponseInfo info, long sentAt, long receivedAt) {
    boolean decoded = false;
    for (Map.Entry<String, String> h : info.getAllHeadersAsList()) {
      decoded |=
          h.getKey().equalsIgnoreCase("Content-Encoding")
              && DECODED.contains(h.getValue().trim().toLowerCase(Locale.ROOT));
    }
    Headers.Builder headers = new Headers.Builder();
    for (Map.Entry<String, String> h : info.getAllHeadersAsList()) {
      String name = h.getKey();
      // Cronet hands back bodies in these encodings decoded
      if (decoded
          && (name.equalsIgnoreCase("Content-Encoding") || name.equalsIgnoreCase("Content-Length")))
        continue;
      headers.addUnsafeNonAscii(name, h.getValue());
    }
    String message = info.getHttpStatusText();
    return new Response.Builder()
        .request(request)
        .protocol("h2".equals(info.getNegotiatedProtocol()) ? Protocol.HTTP_2 : Protocol.HTTP_1_1)
        .code(info.getHttpStatusCode())
        .message(message == null ? "" : message)
        .headers(headers.build())
        .handshake(request.isHttps() ? HANDSHAKE : null)
        .sentRequestAtMillis(sentAt)
        .receivedResponseAtMillis(receivedAt);
  }
}
