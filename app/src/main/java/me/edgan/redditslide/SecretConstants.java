package me.edgan.redditslide;

import android.content.Context;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Created by Deadl on 26/11/2015. */
public class SecretConstants {
    /** Placeholders used when secretconstants.properties is missing the key or unreadable. */
    private static final String IMGUR_TESTING_KEY =
            "3P3GlZj91emshgWU6YuQL98Q9Zihp1c2vCSjsnOQLIchXPzDLh";

    private static final String REDGIFS_EXAMPLE_CLIENT_ID = "93013a4b39f-2031-b319-3021-c4ea3ba7dc12";
    private static final String REDGIFS_EXAMPLE_SECRET = "OcGrW2TjlEa3N349Vs+gQSKu5vTBx19jC/gDXzIFOe4=";

    @Nullable private static String apiKey;
    @Nullable private static String apiImgurKey;
    @Nullable private static String googleLongClientID;
    @Nullable private static String googleShortClientID;
    @Nullable private static String redGifClientId;
    @Nullable private static String redGifClientSecret;

    @Nullable private static String base64EncodedPublicKey;

    public static String getBase64EncodedPublicKey(Context context) {
        if (base64EncodedPublicKey == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                base64EncodedPublicKey = properties.getProperty("base64EncodedPublicKey", "");
            } catch (IOException e) {
                // file not found
                base64EncodedPublicKey = "";
            }
        }
        return base64EncodedPublicKey;
    }

    public static String getApiKey(Context context) {
        if (apiKey == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                apiKey = properties.getProperty("apiKey", "");
            } catch (IOException e) {
                // file not found
                apiKey = "";
            }
        }
        return apiKey;
    }

    public static String getGoogleLongClientID(Context context) {
        if (googleLongClientID == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                googleLongClientID = properties.getProperty("googleLongClientID", "");
            } catch (IOException e) {
                // file not found
                googleLongClientID = "";
            }
        }
        return googleLongClientID;
    }

    public static String getGoogleShortClientID(Context context) {
        if (googleShortClientID == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                googleShortClientID = properties.getProperty("googleShortClientID", "");
            } catch (IOException e) {
                // file not found
                googleShortClientID = "";
            }
        }
        return googleShortClientID;
    }

    public static String getImgurApiKey(Context context) {
        if (apiImgurKey == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                apiImgurKey = properties.getProperty("imgur", IMGUR_TESTING_KEY);
            } catch (IOException e) {
                // file not found
                apiImgurKey = IMGUR_TESTING_KEY;
            }
        }
        return apiImgurKey;
    }

    public static String getRedGifsClientId(Context context) {
        if (redGifClientId == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                redGifClientId = properties.getProperty("redGifClientId", REDGIFS_EXAMPLE_CLIENT_ID);
            } catch (IOException e) {
                // file not found
                redGifClientId = REDGIFS_EXAMPLE_CLIENT_ID;
            }
        }
        return redGifClientId;
    }

    public static String getRedGifsClientSecret(Context context) {
        if (redGifClientSecret == null) {
            InputStream input;
            try {
                input = context.getAssets().open("secretconstants.properties");
                Properties properties = new Properties();
                properties.load(input);
                redGifClientSecret = properties.getProperty("redGifClientSecret", REDGIFS_EXAMPLE_SECRET);
            } catch (IOException e) {
                // file not found
                redGifClientSecret = REDGIFS_EXAMPLE_SECRET;
            }
        }
        return redGifClientSecret;
    }

}
