package gitruler;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

class GitRulerConfig {

    List<Map<String, Object>> rules = new ArrayList<>();

    GitRulerConfig(String path) throws IOException {

        String configData = FileUtils.readFileToString(new File(path), Charset.forName("UTF-8"));
        JSONArray rulesJson = new JSONArray(configData);

        for (int i = 0; i < rulesJson.length(); ++i) {

            Map<String, Object> ruleDetails = new HashMap<>();
            rules.add(ruleDetails);

            JSONObject rule = rulesJson.getJSONObject(i);

            Iterator<?> keys = rule.keys();

            while( keys.hasNext() ) {
                String key = (String)keys.next();
                ruleDetails.put(key, rule.get(key));
            }
        }
    }
}
