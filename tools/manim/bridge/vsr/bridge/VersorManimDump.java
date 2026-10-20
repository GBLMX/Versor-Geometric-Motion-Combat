package vsr.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import vsr.E3;
import vsr.Multivector;

/**
 * Dumps an E3 rotor path for {@code tools/manim/versor_scene.py}.
 *
 * <p>The numerical work is entirely in versor-java: a z-axis rotor rotates
 * {@code e1}, and the scene only interpolates the exported coordinates.</p>
 */
public final class VersorManimDump {
    private static final int SAMPLES = 61;
    private static final double MAX_ANGLE = Math.PI / 2;

    private VersorManimDump() {}

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "data/versor-rotor.json");
        Files.createDirectories(output.toAbsolutePath().getParent());

        StringBuilder json = new StringBuilder(4096);
        json.append("{\n  \"source\": \"versor-java\",\n  \"axis\": [0.0, 0.0, 1.0],\n  \"samples\": [\n");

        double maxNormError = 0.0;
        for (int i = 0; i < SAMPLES; i++) {
            double t = i / (double) (SAMPLES - 1);
            double angle = MAX_ANGLE * t;
            Multivector rotor = E3.rotor(0, 0, 1, angle);
            Multivector image = E3.rotate(E3.vec(1, 0, 0), rotor);
            double[] v = E3.coords(image);
            double norm = Math.hypot(v[0], v[1]);
            maxNormError = Math.max(maxNormError, Math.abs(norm - 1.0));

            json.append("    {\"t\": ").append(num(t))
                    .append(", \"angle\": ").append(num(angle))
                    .append(", \"x\": ").append(num(v[0]))
                    .append(", \"y\": ").append(num(v[1]))
                    .append('}');
            if (i + 1 < SAMPLES) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Files.writeString(output, json.toString());

        if (maxNormError > 1e-9) {
            throw new AssertionError("rotor did not preserve length: " + maxNormError);
        }
        System.out.printf(Locale.ROOT, "samples=%d maxNormError=%.3e output=%s%n",
                SAMPLES, maxNormError, output);
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.12f", value);
    }
}
