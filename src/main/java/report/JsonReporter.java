package report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;

public final class JsonReporter {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static String toJson(Map<String, Set<String>> smellToMethods) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> smells = new ArrayList<>(smellToMethods.keySet());
        Collections.sort(smells);

        for (String smell : smells) {
            Set<String> methodsSet = smellToMethods.getOrDefault(smell, Collections.emptySet());
            List<String> methods = new ArrayList<>(methodsSet);
            Collections.sort(methods);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("methods", methods);
            out.put(smell, entry);
        }
        return GSON.toJson(out);
    }

    private JsonReporter() {}
}
