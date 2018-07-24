package gitruler;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

class GitRulerConfig {

    private List<Rule> rules = new ArrayList<>();
    private Map<String, String> setupFiles= new HashMap<>();

    GitRulerConfig(String path) throws IOException {

        String configData = FileUtils.readFileToString(new File(path), Charset.forName("UTF-8"));

        JSONObject rulesRoot = new JSONObject(configData);
        JSONArray rulesJson = rulesRoot.getJSONArray("rules");

        for (int i = 0; i < rulesJson.length(); ++i) {

            Map<String, Object> ruleDetails = new HashMap<>();

            JSONObject rule = rulesJson.getJSONObject(i);
            Iterator<?> keys = rule.keys();

            while (keys.hasNext()) {
                String key = (String) keys.next();
                ruleDetails.put(key, rule.get(key));
            }
            rules.add(new Rule(ruleDetails));
        }

        // Create the files in the setup
        JSONArray setupJson = rulesRoot.getJSONArray("setup-files");
        for (int i = 0; i < setupJson.length(); ++i) {
            JSONObject file = setupJson.getJSONObject(i);
            setupFiles.put(file.getString("path"), file.getString("contents"));
        }
    }

    Iterable<Rule> getRules() {
        return rules;
    }

    Map<String, String> getSetupFiles() {
        return setupFiles;
    }
}
