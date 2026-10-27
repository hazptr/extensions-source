package eu.kanade.tachiyomi.extension.all.keiyoushiruntime.cronet;

import android.content.Context;
import android.content.ContextWrapper;
import com.google.android.gms.common.GooglePlayServicesUtilLight;
import com.google.android.gms.net.CronetProviderInstaller;
import okhttp3.Interceptor;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;

/** Entry point for lib:cronet; only framework and OkHttp types cross the class loader boundary. */
public final class CronetRuntime {
  private static volatile Interceptor server;
  private static boolean started;

  private CronetRuntime() {}

  /**
   * Sends one request over the device's Cronet; null until Cronet is ready or when the device has
   * none.
   */
  public static Interceptor server(Context context) {
    if (server == null) start(new LoaderContext(context.getApplicationContext()));
    return server;
  }

  private static synchronized void start(Context context) {
    if (started) return;
    started = true;
    if (use(context, "HttpEngine-Native-Provider")) return;
    // The app's manifest has no Play services version tag
    GooglePlayServicesUtilLight.enableUsingApkIndependentContext();
    CronetProviderInstaller.installProvider(context)
        .addOnSuccessListener(
            r -> new Thread(r, "KeiCronet").start(),
            unused -> use(context, "Google-Play-Services-Cronet-Provider"));
  }

  /** Also runs on Play services' callback thread, where a throw would take the app down */
  private static boolean use(Context context, String name) {
    try {
      for (CronetProvider provider : CronetProvider.getAllProviders(context)) {
        if (provider.getName().equals(name) && provider.isEnabled()) {
          CronetEngine engine =
              provider
                  .createBuilder()
                  .enableHttp2(true)
                  .enableQuic(false)
                  .enableBrotli(true)
                  .build();
          server = new CronetServer(engine);
          return true;
        }
      }
    } catch (Throwable t) {
      // No Cronet: requests stay on OkHttp
    }
    return false;
  }

  /** Cronet finds its providers through the context's class loader */
  private static final class LoaderContext extends ContextWrapper {
    LoaderContext(Context base) {
      super(base);
    }

    @Override
    public Context getApplicationContext() {
      return this;
    }

    @Override
    public ClassLoader getClassLoader() {
      return CronetRuntime.class.getClassLoader();
    }
  }
}
