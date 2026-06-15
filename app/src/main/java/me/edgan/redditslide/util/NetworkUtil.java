package me.edgan.redditslide.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.core.content.ContextCompat;

import me.edgan.redditslide.Reddit;

/**
 * Collection of various network utility methods.
 *
 * @author Matthew Dean
 */
public class NetworkUtil {

    private NetworkUtil() {}

    /**
     * Uses the provided context to determine the current connectivity status.
     *
     * @param context A context used to retrieve connection information.
     * @return A non-null value defined in {@link Status}.
     */
    private static Status getConnectivityStatus(final Context context) {
        // Check if in forced offline mode
        if (Reddit.appRestart != null && Reddit.appRestart.getBoolean("forceoffline", false)) {
            return Status.NONE;
        }

        // For normal operation, always return WIFI status unless in forced offline mode.
        // This intentionally keeps auto-offline disabled (see "Disabling auto-offline" commit).
        // The real connection type is only consulted for the WiFi-vs-mobile distinction needed
        // by data saving; see getRealConnectivityStatus().
        return Status.WIFI;
    }

    /**
     * Determines the actual connection type from the system, used to distinguish WiFi from
     * metered/mobile connections for data-saving settings. Unlike {@link
     * #getConnectivityStatus(Context)} this is not overridden to always report WiFi.
     */
    private static Status getRealConnectivityStatus(final Context context) {
        return getConnectivityStatusNew(context);
    }

    private static Status getConnectivityStatusNew(final Context context) {
        final ConnectivityManager cm =
                ContextCompat.getSystemService(context, ConnectivityManager.class);
        if (cm == null) {
            return Status.NONE;
        }

        final Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return Status.NONE;
        }

        final NetworkCapabilities nwCapabilities = cm.getNetworkCapabilities(activeNetwork);
        if (nwCapabilities == null) {
            return Status.NONE;
        }

        if (!isConnectedToInternet(nwCapabilities)) {
            return Status.NONE;
        }

        // Don't rely on wifiManager.isWifiEnabled() which may be affected by permissions
        // Instead rely solely on NetworkCapabilities to determine connection type
        if (nwCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || nwCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            if (cm.isActiveNetworkMetered())
                return Status.MOBILE; // respect metered wifi networks as mobile
            return Status.WIFI;
        }
        if (nwCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || nwCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return Status.MOBILE;
        }
        return Status.NONE;
    }

    /**
     * Checks if the network is connected. An application context is said to have connection if
     * {@link #getConnectivityStatus(Context)} does not equal {@link Status#NONE}.
     *
     * @param context The context used to retrieve connection information.
     * @return true if the application is connected, false if otherwise.
     */
    public static boolean isConnected(final Context context) {
        // Check if in forced offline mode
        if (Reddit.appRestart != null && Reddit.appRestart.getBoolean("forceoffline", false)) {
            return false;
        }
        // Always return true unless in offline mode
        return true;
    }

    public static boolean isConnectedNoOverride(final Context context) {
        return getConnectivityStatus(context) != Status.NONE;
    }

    /**
     * Checks if the network is connected to WiFi.
     *
     * @param context The context used to retrieve connection information.
     * @return true if the application is connected, false if otherwise.
     */
    public static boolean isConnectedWifi(Context context) {
        // Check if in forced offline mode
        if (Reddit.appRestart != null && Reddit.appRestart.getBoolean("forceoffline", false)) {
            return false;
        }
        // Determine the real connection type so data-saving settings ("Mobile data" only)
        // can correctly distinguish WiFi from metered/mobile connections. Note that
        // isConnected()/isConnectedNoOverride() intentionally stay always-online to keep
        // auto-offline disabled; only the WiFi-vs-mobile determination relies on the real
        // connectivity status here.
        return getRealConnectivityStatus(context) == Status.WIFI;
    }

    private static boolean isConnectedToInternet(final NetworkCapabilities nwCapabilities) {
        return nwCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && nwCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * A simplified list of connectivity statuses. See {@link ConnectivityManager}'s {@code TYPE_*}
     * for a full list.
     */
    private enum Status {
        /** Operating on a wireless connection. */
        WIFI,
        /** Operating on 3G, 4G, 4G LTE, etc. */
        MOBILE,
        /** No connection present. */
        NONE
    }
}
