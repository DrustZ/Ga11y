package demo.AnnotationSystem.Identifier;

import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

public class Repairs {
    private JsonParser parser = new JsonParser();
    private JsonObject repairs;

    public Repairs(Context context) {
        try {
            InputStreamReader reader = new InputStreamReader(context.getAssets().open("repairs.json"));
            repairs = parser.parse(reader).getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<JsonObject> getTemplateScreen(Screen screen, String packageName, String versionName) {
        JsonObject temp;
        JsonArray screens = null;
        ArrayList<JsonObject> matchingScreens = new ArrayList<>();
        if (repairs.has(packageName)) {
            temp = repairs.getAsJsonObject(packageName);
            if (!temp.entrySet().isEmpty()) {
                for (Map.Entry<String, JsonElement> entry : temp.entrySet()) {
                    screens = entry.getValue().getAsJsonArray();
                    break;
                }
            }
            if (temp.has(versionName)) {
                screens = temp.getAsJsonArray(versionName);
            }
            if (packageName.equals("app.mywed.android")) {
                screens = temp.getAsJsonArray("1.16.85");
            }
            if (packageName.equals("com.yelp.android")) {
                screens = temp.getAsJsonArray("19.48.0-21095303");
            }
        }

        Log.e("Repairs", "" + (screens != null));

        if (screens == null) return matchingScreens;

        for (JsonElement screenEle : screens) {
            JsonObject screenObj = screenEle.getAsJsonObject();
            JsonObject heuristicsObj = screenObj.getAsJsonObject("heuristics");
            if (ScreenEquivalenceKt.isSameScreenJson(screen, heuristicsObj, true)) {
                matchingScreens.add(screenObj);
            }
        }
        return matchingScreens;
    }

}
