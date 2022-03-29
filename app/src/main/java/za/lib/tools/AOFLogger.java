package za.lib.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Base64;
import java.util.Map;

public class AOFLogger extends Writer {
    private final BufferedWriter bw;
    private final FileWriter fw;

    public AOFLogger(String fileName) throws IOException {
        new File(fileName).createNewFile();  // todo create directories
        fw = new FileWriter(fileName, /* append */ true);
        bw = new BufferedWriter(fw);
    }

    @Override
    public void close() throws IOException {
        // todo ordering?
        // todo do we need to close bw&fw or just bw?
        try {
            bw.close();
        } finally {
            fw.close();
        }
    }

    @Override
    public void flush() throws IOException {
        bw.flush();
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        bw.write(cbuf, off, len);
    }

    // todo json object instead of stringly typed map
    public void write(Map<String, String> flatJson) throws IOException {
        var json = base64Jsonify(flatJson);
        bw.append(json);
        bw.newLine();
    }

    @Override
    public void write(String line) throws IOException {
        bw.append(line);
        bw.newLine();
    }

    private static String base64Encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    // assumes that keys are safe to write as-is; only values are base64-encoded
    private static String base64Jsonify(Map<String, String> json) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        int i = 0;
        for (var kv : json.entrySet()) {
            var key = kv.getKey();
            var val = kv.getValue();
            sb.append('"');
            sb.append(key);
            sb.append("\":");
            sb.append(base64Encode(val));
            sb.append('\"');
            if (i < json.size()-1) {
                sb.append(',');
            }
            i++;
        }
        sb.append('}');
        return sb.toString();
    }
}
