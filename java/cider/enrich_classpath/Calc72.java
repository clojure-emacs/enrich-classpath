package cider.enrich_classpath;

import java.io.OutputStream;
import java.io.IOException;

public class Calc72 {
    public static OutputStream calc72(OutputStream out, String line, boolean accountForNewlines) throws IOException {
        if (!line.isEmpty()) {
            byte[] lineBytes = line.getBytes("UTF-8");
            int length = lineBytes.length;
            out.write(lineBytes[0]);
            int pos = 1;
            int threshold = accountForNewlines ? 69 : 71;
            while (length - pos > threshold) {
                out.write(lineBytes, pos, threshold);
                pos += threshold;
                out.write('\r');
                out.write('\n');
                out.write(' ');
            }
            out.write(lineBytes, pos, length - pos);
        }
        return out;
    }
}
