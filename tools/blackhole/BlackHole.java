import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import vsr.E3;
import vsr.Multivector;
import vsr.P3;

/**
 * CPU Schwarzschild black-hole renderer whose geodesic state and disk geometry
 * are built with Clifford algebra from {@code versor-java}.
 *
 * <p>Units: {@code G = c = 1}, Schwarzschild radius {@code rs = 1}, mass
 * {@code M = rs/2}. The photon path obeys the exact null-geodesic central-force
 * equation
 *
 * <pre>{@code
 *   L = x ^ v,   h2 = -L L,   x'' = -(3/2) h2 x / r^5
 * }</pre>
 *
 * <p>The first two expressions are E3 geometric algebra. The accretion-disk
 * plane and its ray intersections are PGA3 elements. The inner integration loop
 * uses scalar coefficients after that setup so a single image does not allocate
 * a multivector per step; {@code --validate} exercises the same equations and
 * the PGA meet directly.
 */
public final class BlackHole {
    private static final double RS = 1.0;
    private static final double MASS = 0.5;
    private static final double DISK_IN = 3.0 * RS;
    private static final double DISK_OUT = 14.0 * RS;
    private static final double CRITICAL_B = 1.5 * Math.sqrt(3.0) * RS;
    private static final double EVENT_HORIZON = 1.0001 * RS;
    private static final int DEFAULT_STEPS = 700;
    private static final Multivector DISK_PLANE = P3.plane(0, 1, 0, 0);
    private static final double[] GALACTIC_NORMAL = normalize3(0.34, 0.82, 0.46);
    private static final double[] GALAXY_CENTER = normalize3(0.28, 0.18, -1.0);
    private static final double[] GALAXY_TANGENT = normalize3(
            GALACTIC_NORMAL[1] * GALAXY_CENTER[2] - GALACTIC_NORMAL[2] * GALAXY_CENTER[1],
            GALACTIC_NORMAL[2] * GALAXY_CENTER[0] - GALACTIC_NORMAL[0] * GALAXY_CENTER[2],
            GALACTIC_NORMAL[0] * GALAXY_CENTER[1] - GALACTIC_NORMAL[1] * GALAXY_CENTER[0]);
    private static final double[] GALAXY_BITANGENT = normalize3(
            GALAXY_CENTER[1] * GALAXY_TANGENT[2] - GALAXY_CENTER[2] * GALAXY_TANGENT[1],
            GALAXY_CENTER[2] * GALAXY_TANGENT[0] - GALAXY_CENTER[0] * GALAXY_TANGENT[2],
            GALAXY_CENTER[0] * GALAXY_TANGENT[1] - GALAXY_CENTER[1] * GALAXY_TANGENT[0]);

    private BlackHole() {}

    public static void main(String[] args) throws Exception {
        int width = 640;
        int height = 640;
        int samples = 1;
        int steps = DEFAULT_STEPS;
        double fovDegrees = 50.0;
        String output = "tools/blackhole/black_hole.png";
        boolean validate = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--width" -> width = Integer.parseInt(require(args, ++i, "--width"));
                case "--height" -> height = Integer.parseInt(require(args, ++i, "--height"));
                case "--samples" -> samples = Integer.parseInt(require(args, ++i, "--samples"));
                case "--steps" -> steps = Integer.parseInt(require(args, ++i, "--steps"));
                case "--fov" -> fovDegrees = Double.parseDouble(require(args, ++i, "--fov"));
                case "--out" -> output = require(args, ++i, "--out");
                case "--validate" -> validate = true;
                case "--help", "-h" -> {
                    usage();
                    return;
                }
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }

        if (width < 16 || height < 16 || samples < 1 || samples > 16 || steps < 32) {
            throw new IllegalArgumentException("width/height >= 16, samples 1..16, steps >= 32");
        }
        if (validate) {
            validate();
            if (args.length == 1) return;
        }

        long start = System.nanoTime();
        BufferedImage image = render(width, height, samples, steps, fovDegrees);
        File file = new File(output);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        if (!ImageIO.write(image, "png", file)) {
            throw new IllegalStateException("no PNG writer available");
        }
        System.out.printf(Locale.ROOT,
                "rendered %dx%d, %d sample(s), %d steps -> %s in %.2f s%n",
                width, height, samples, steps, file,
                (System.nanoTime() - start) / 1e9);
    }

    private static String require(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException(option + " needs a value");
        return args[index];
    }

    private static void usage() {
        System.out.println("""
                Usage: BlackHole [options]
                  --width N       image width (default 640)
                  --height N      image height (default 640)
                  --samples N     per-pixel samples, 1..16 (default 1)
                  --steps N       max geodesic steps per ray (default 700)
                  --fov DEG       vertical field of view (default 50)
                  --out PATH      PNG output (default tools/blackhole/black_hole.png)
                  --validate      run analytic/geometric checks, then render
                  --help          this text
                """);
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private static BufferedImage render(int width, int height, int samples, int steps, double fovDegrees) {
        Camera camera = Camera.lookingAt(
                E3.vec(0.0, 3.0, 24.0),
                E3.vec(0.0, 0.0, 0.0),
                E3.vec(0.0, 1.0, 0.0),
                fovDegrees);
        int[] pixels = new int[width * height];
        double invSamples = 1.0 / samples;

        IntStream.range(0, height).parallel().forEach(py -> {
            for (int px = 0; px < width; px++) {
                double r = 0.0;
                double g = 0.0;
                double b = 0.0;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        double ox = (sx + 0.5) / samples;
                        double oy = (sy + 0.5) / samples;
                        double[] dir = camera.ray(px + ox, py + oy, width, height);
                        double[] c = trace(camera.eye, dir, steps);
                        r += c[0];
                        g += c[1];
                        b += c[2];
                    }
                }
                pixels[py * width + px] = pack(r * invSamples, g * invSamples, b * invSamples);
            }
        });

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /** A camera frame represented by E3 vectors. */
    private record Camera(double[] eye, double[] forward, double[] right, double[] up,
                          double tanHalfFov) {
        static Camera lookingAt(Multivector eye, Multivector target, Multivector worldUp, double fovDegrees) {
            Multivector forward = target.sub(eye).unit();
            Multivector right = cross(forward, worldUp).unit();
            Multivector up = cross(right, forward).unit();
            double[] e = E3.coords(eye);
            double[] f = E3.coords(forward);
            double[] r = E3.coords(right);
            double[] u = E3.coords(up);
            return new Camera(e, f, r, u, Math.tan(Math.toRadians(fovDegrees * 0.5)));
        }

        double[] ray(double px, double py, int width, int height) {
            double aspect = (double) width / height;
            double sx = (2.0 * px / width - 1.0) * aspect * tanHalfFov;
            double sy = (1.0 - 2.0 * py / height) * tanHalfFov;
            double x = forward[0] + right[0] * sx + up[0] * sy;
            double y = forward[1] + right[1] * sx + up[1] * sy;
            double z = forward[2] + right[2] * sx + up[2] * sy;
            double n = Math.sqrt(x * x + y * y + z * z);
            return new double[]{x / n, y / n, z / n};
        }
    }

    /**
     * Trace one photon. Returns linear RGB; a captured path contributes no sky.
     */
    private static double[] trace(double[] eye, double[] dir, int maxSteps) {
        double x = eye[0], y = eye[1], z = eye[2];
        double vx = dir[0], vy = dir[1], vz = dir[2];
        double h2 = angularMomentumSq(x, y, z, vx, vy, vz);
        double transmittance = 1.0;
        double red = 0.0, green = 0.0, blue = 0.0;
        boolean captured = false;

        for (int i = 0; i < maxSteps; i++) {
            double r2 = x * x + y * y + z * z;
            double r = Math.sqrt(r2);
            if (r < EVENT_HORIZON) {
                captured = true;
                break;
            }
            if (r > 80.0 && (x * vx + y * vy + z * vz) > 0.0) break;

            double dt = Math.min(0.8, 0.012 + 0.035 * r + 0.020 * h2 / (r2 + 1.0));
            double oldX = x, oldY = y, oldZ = z;
            double oldVx = vx, oldVy = vy, oldVz = vz;

            // Fourth-order Runge-Kutta. ACCEL(x) = -1.5 h2 x / r^5.
            double k1 = -1.5 * h2 / (r2 * r2 * r);
            double ax1 = k1 * x, ay1 = k1 * y, az1 = k1 * z;
            double x2 = x + 0.5 * dt * vx, y2 = y + 0.5 * dt * vy, z2 = z + 0.5 * dt * vz;
            double vx2 = vx + 0.5 * dt * ax1, vy2 = vy + 0.5 * dt * ay1, vz2 = vz + 0.5 * dt * az1;
            double r22 = x2 * x2 + y2 * y2 + z2 * z2;
            double k2 = -1.5 * h2 / (r22 * r22 * Math.sqrt(r22));
            double ax2 = k2 * x2, ay2 = k2 * y2, az2 = k2 * z2;
            double x3 = x + 0.5 * dt * vx2, y3 = y + 0.5 * dt * vy2, z3 = z + 0.5 * dt * vz2;
            double vx3 = vx + 0.5 * dt * ax2, vy3 = vy + 0.5 * dt * ay2, vz3 = vz + 0.5 * dt * az2;
            double r23 = x3 * x3 + y3 * y3 + z3 * z3;
            double k3 = -1.5 * h2 / (r23 * r23 * Math.sqrt(r23));
            double ax3 = k3 * x3, ay3 = k3 * y3, az3 = k3 * z3;
            double x4 = x + dt * vx3, y4 = y + dt * vy3, z4 = z + dt * vz3;
            double vx4 = vx + dt * ax3, vy4 = vy + dt * ay3, vz4 = vz + dt * az3;
            double r24 = x4 * x4 + y4 * y4 + z4 * z4;
            double k4 = -1.5 * h2 / (r24 * r24 * Math.sqrt(r24));
            double ax4 = k4 * x4, ay4 = k4 * y4, az4 = k4 * z4;

            x += dt / 6.0 * (vx + 2.0 * vx2 + 2.0 * vx3 + vx4);
            y += dt / 6.0 * (vy + 2.0 * vy2 + 2.0 * vy3 + vy4);
            z += dt / 6.0 * (vz + 2.0 * vz2 + 2.0 * vz3 + vz4);
            vx += dt / 6.0 * (ax1 + 2.0 * ax2 + 2.0 * ax3 + ax4);
            vy += dt / 6.0 * (ay1 + 2.0 * ay2 + 2.0 * ay3 + ay4);
            vz += dt / 6.0 * (az1 + 2.0 * az2 + 2.0 * az3 + az4);

            if (oldY * y < 0.0) {
                double[] hit = diskHit(oldX, oldY, oldZ, x, y, z);
                if (hit != null) {
                    double rh = Math.hypot(hit[0], hit[2]);
                    if (rh > DISK_IN && rh < DISK_OUT) {
                        double dx = oldVx + (vx - oldVx) * hit[3];
                        double dy = oldVy + (vy - oldVy) * hit[3];
                        double dz = oldVz + (vz - oldVz) * hit[3];
                        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        double[] emission = diskEmission(hit[0], hit[1], hit[2], dx / n, dy / n, dz / n);
                        red += transmittance * emission[0];
                        green += transmittance * emission[1];
                        blue += transmittance * emission[2];
                        transmittance *= 0.16;
                        if (transmittance < 0.01) break;
                    }
                }
            }
        }

        double impact = Math.sqrt(h2);
        double ring = Math.exp(-Math.pow((impact - CRITICAL_B) / 0.045, 2.0));
        if (!captured) {
            double n = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double[] sky = sky(vx / n, vy / n, vz / n);
            // Light from the celestial sphere is magnified strongly near the
            // critical curve; the geodesic direction itself is already the
            // lensed direction. This small boost makes the lensed arcs legible.
            double lensing = 1.0 + 1.8 * ring;
            red += transmittance * sky[0] * lensing;
            green += transmittance * sky[1] * lensing;
            blue += transmittance * sky[2] * lensing;
        }
        red += ring * 0.045;
        green += ring * 0.065;
        blue += ring * 0.12;
        return new double[]{red, green, blue};
    }

    /**
     * PGA3 intersection of the y=0 disk plane with the segment between two
     * states. Returns {x,y,z,parameter}; y is zero by construction.
     */
    private static double[] diskHit(double x0, double y0, double z0,
                                    double x1, double y1, double z1) {
        Multivector p0 = P3.point(x0, y0, z0);
        Multivector p1 = P3.point(x1, y1, z1);
        Multivector line = P3.line(p0, p1);
        Multivector meet = P3.meetPlaneLine(DISK_PLANE, line);
        if (meet.maxAbs() < 1e-12) return null;
        double[] p = P3.pointCoords(meet);
        double t = y0 / (y0 - y1);
        return new double[]{p[0], p[1], p[2], t};
    }

    /** Correct relativistic Doppler + gravitational shift, thin-disk emission. */
    private static double[] diskEmission(double x, double y, double z,
                                         double dx, double dy, double dz) {
        double r = Math.hypot(x, z);
        if (r <= DISK_IN || r >= DISK_OUT) return new double[3];

        double xin = DISK_IN / r;
        double profile = Math.pow(xin, 0.75)
                * Math.pow(Math.max(0.0, 1.0 - Math.sqrt(xin)), 0.25) / 0.4878;
        if (profile <= 1e-6) return new double[3];

        // Orbital velocity in the disk plane. The photon direction stored in
        // the tracer is camera -> disk, so the emitter -> observer direction is -rayDir.
        double speed = Math.min(0.95, Math.sqrt(MASS / r));
        double phiX = -z / r;
        double phiZ = x / r;
        double betaX = speed * phiX;
        double betaZ = speed * phiZ;
        double gamma = 1.0 / Math.sqrt(Math.max(1e-6, 1.0 - speed * speed));
        double dot = betaX * (-dx) + betaZ * (-dz);
        double doppler = 1.0 / Math.max(1e-4, gamma * (1.0 - dot));
        doppler = clamp(doppler, 0.2, 4.0);
        double grav = Math.sqrt(Math.max(1e-4, 1.0 - RS / r));
        double shift = doppler * grav;

        double temperature = 1200.0 + 9000.0 * Math.pow(profile, 2.0);
        double observedTemperature = temperature * shift;
        double[] rgb = saturate(blackbody(observedTemperature), 2.4);

        double phi = Math.atan2(z, x);
        double smoothNoise = noise2(x * 0.85 + 13.0, z * 0.85 - 7.0);
        double fineNoise = noise2(x * 2.7 - 4.0, z * 2.7 + 19.0);
        double turbulence = 0.58
                + 0.24 * smoothNoise
                + 0.12 * fineNoise
                + 0.10 * Math.sin(7.0 * phi + 12.0 * Math.log(r))
                * Math.sin(5.0 * phi - 9.0 * Math.log(r))
                + 0.06 * Math.sin(23.0 * Math.log(r) + 2.0 * phi);
        turbulence = clamp(turbulence, 0.22, 1.35);

        // Warm the outer disk while keeping the inner rim nearly white.
        double warmth = 1.0 - smoothstep(0.35, 0.95, profile);
        double tintR = rgb[0] * (1.0 + 0.22 * warmth);
        double tintG = rgb[1] * (1.0 - 0.04 * warmth);
        double tintB = rgb[2] * (1.0 - 0.42 * warmth);

        // Thin-sheet optical path grows toward edge-on viewing.
        double path = Math.min(5.0, 1.0 / (Math.abs(dy) + 0.28));
        double rim = Math.exp(-Math.pow((r - 3.32) / 0.24, 2.0));
        double outerFade = 1.0 - smoothstep(9.0, DISK_OUT, r);
        double emissivity = (Math.pow(profile, 5.0) * Math.pow(shift, 4.0)
                * turbulence * path * 0.65 + 0.55 * rim * path) * outerFade;
        return new double[]{tintR * emissivity, tintG * emissivity, tintB * emissivity};
    }

    // ---------------------------------------------------------------------
    // Blackbody colour (Planckian locus approximation)
    // ---------------------------------------------------------------------

    private static double[] blackbody(double temperature) {
        double t = clamp(temperature, 1000.0, 40000.0);
        double t2 = t * t;
        double t3 = t2 * t;
        double x;
        if (t < 4000.0) {
            x = -0.2661239e9 / t3 - 0.2343589e6 / t2 + 0.8776956e3 / t + 0.179910;
        } else {
            x = -3.0258469e9 / t3 + 2.1070379e6 / t2 + 0.2226347e3 / t + 0.240390;
        }
        double y;
        if (t < 2222.0) {
            y = -1.1063814 * x * x * x - 1.34811020 * x * x + 2.18555832 * x - 0.20219683;
        } else if (t < 4000.0) {
            y = -0.9549476 * x * x * x - 1.37418593 * x * x + 2.09137015 * x - 0.16748867;
        } else {
            y = 3.0817580 * x * x * x - 5.87338670 * x * x + 3.75112997 * x - 0.37001483;
        }
        y = Math.max(y, 1e-3);
        double capitalX = x / y;
        double capitalZ = (1.0 - x - y) / y;
        double r = 3.2406 * capitalX - 1.5372 - 0.4986 * capitalZ;
        double g = -0.9689 * capitalX + 1.8758 + 0.0415 * capitalZ;
        double b = 0.0557 * capitalX - 0.2040 + 1.0570 * capitalZ;
        r = Math.max(0.0, r);
        g = Math.max(0.0, g);
        b = Math.max(0.0, b);
        double max = Math.max(r, Math.max(g, b));
        if (max < 1e-9) return new double[]{1.0, 1.0, 1.0};
        return new double[]{r / max, g / max, b / max};
    }

    // ---------------------------------------------------------------------
    // Sky
    // ---------------------------------------------------------------------

    private static double[] sky(double x, double y, double z) {
        double[] color = new double[3];

        // A tilted galactic band with dust and cool haze. This is evaluated on
        // the final (lensed) direction, so the geodesics visibly warp it.
        double latitude = x * GALACTIC_NORMAL[0] + y * GALACTIC_NORMAL[1] + z * GALACTIC_NORMAL[2];
        double dust = fbm3(x * 7.0 + 4.0, y * 7.0 - 3.0, z * 7.0 + 8.0, 4);
        double band = Math.exp(-Math.pow(latitude / 0.20, 2.0))
                * (0.52 + 0.70 * dust);
        color[0] += band * 0.006;
        color[1] += band * 0.008;
        color[2] += band * 0.018;

        // Low-frequency emission clouds plus a finer filament field.
        double cloud = fbm3(x * 2.15 + 11.0, y * 2.15 - 7.0, z * 2.15 + 3.0, 4);
        double filament = fbm3(x * 5.4 - 4.0, y * 5.4 + 9.0, z * 5.4 - 2.0, 4);
        double nebula = smoothstep(0.50, 0.82, cloud * 0.80 + filament * 0.32);
        double magenta = smoothstep(0.42, 0.78, filament);
        color[0] += nebula * (0.016 + 0.035 * magenta);
        color[1] += nebula * (0.007 + 0.005 * (1.0 - magenta));
        color[2] += nebula * (0.032 + 0.022 * (1.0 - magenta));

        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        double u;
        double v;
        int face;
        if (ax >= ay && ax >= az) {
            u = y / ax;
            v = z / ax;
            face = x >= 0 ? 0 : 1;
        } else if (ay >= az) {
            u = x / ay;
            v = z / ay;
            face = y >= 0 ? 2 : 3;
        } else {
            u = x / az;
            v = y / az;
            face = z >= 0 ? 4 : 5;
        }

        addStars(color, u, v, face, 150.0, 0.9950, 1.1);
        addStars(color, u, v, face, 280.0, 0.9935, 0.65);
        addStars(color, u, v, face, 520.0, 0.9920, 0.35);

        addGalaxy(color, x, y, z);
        return saturate(color, 2.2);
    }

    /**
     * A small spiral galaxy almost directly behind the hole. Its lensed images
     * provide a clear visual reference for the geodesic distortion.
     */
    private static void addGalaxy(double[] color, double x, double y, double z) {
        double toward = x * GALAXY_CENTER[0] + y * GALAXY_CENTER[1] + z * GALAXY_CENTER[2];
        if (toward < 0.93) return;
        double a = (x * GALAXY_TANGENT[0] + y * GALAXY_TANGENT[1] + z * GALAXY_TANGENT[2]) / toward;
        double b = (x * GALAXY_BITANGENT[0] + y * GALAXY_BITANGENT[1] + z * GALAXY_BITANGENT[2]) / toward;
        double radius = Math.hypot(a, b);
        if (radius > 0.32) return;

        double theta = Math.atan2(b, a);
        double core = Math.exp(-Math.pow(radius / 0.022, 2.0));
        double disk = Math.exp(-Math.pow(radius / 0.075, 2.0));
        double arms = 0.5 + 0.5 * Math.cos(
                2.0 * theta + 7.0 * Math.log(Math.max(radius, 0.008)) - 0.7);
        double dust = fbm3(x * 9.0, y * 9.0, z * 9.0, 3);
        double brightness = core * 0.45
                + disk * (0.045 + 0.13 * arms) * (0.40 + 0.60 * dust);
        double warmth = core;
        color[0] += brightness * (0.55 + 0.55 * warmth);
        color[1] += brightness * (0.68 + 0.25 * warmth);
        color[2] += brightness * (0.92 + 0.08 * warmth);
    }

    private static void addStars(double[] color, double u, double v, int face,
                                 double scale, double threshold, double brightness) {
        double px = u * scale;
        double py = v * scale;
        int ix = (int) Math.floor(px);
        int iy = (int) Math.floor(py);
        double h = hash(ix, iy, face * 131 + (int) scale);
        if (h < threshold) return;
        double fx = px - ix;
        double fy = py - iy;
        double cx = 0.18 + 0.64 * hash(ix + 17, iy - 31, face * 7 + (int) scale);
        double cy = 0.18 + 0.64 * hash(ix - 41, iy + 23, face * 11 + (int) scale);
        double dx = fx - cx;
        double dy = fy - cy;
        double d2 = dx * dx + dy * dy;
        double core = Math.exp(-d2 * 90.0);
        double glow = Math.exp(-d2 * 22.0) * 0.18;
        double amount = (core + glow) * brightness * (0.35 + 0.65 * h);
        double tint = hash(ix + 5, iy + 9, face + 71);
        if (tint < 0.18) {
            color[0] += amount * 0.72;
            color[1] += amount * 0.84;
            color[2] += amount * 1.15;
        } else if (tint > 0.82) {
            color[0] += amount * 1.15;
            color[1] += amount * 0.88;
            color[2] += amount * 0.62;
        } else {
            color[0] += amount;
            color[1] += amount;
            color[2] += amount;
        }
    }

    private static double[] normalize3(double x, double y, double z) {
        double n = Math.sqrt(x * x + y * y + z * z);
        if (n < 1e-12) throw new IllegalArgumentException("zero vector");
        return new double[]{x / n, y / n, z / n};
    }

    private static double noise3(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);
        double uz = fz * fz * (3.0 - 2.0 * fz);
        double c000 = hash(ix, iy, iz);
        double c100 = hash(ix + 1, iy, iz);
        double c010 = hash(ix, iy + 1, iz);
        double c110 = hash(ix + 1, iy + 1, iz);
        double c001 = hash(ix, iy, iz + 1);
        double c101 = hash(ix + 1, iy, iz + 1);
        double c011 = hash(ix, iy + 1, iz + 1);
        double c111 = hash(ix + 1, iy + 1, iz + 1);
        double x00 = c000 + (c100 - c000) * ux;
        double x10 = c010 + (c110 - c010) * ux;
        double x01 = c001 + (c101 - c001) * ux;
        double x11 = c011 + (c111 - c011) * ux;
        double y0 = x00 + (x10 - x00) * uy;
        double y1 = x01 + (x11 - x01) * uy;
        return y0 + (y1 - y0) * uz;
    }

    private static double fbm3(double x, double y, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 0.5;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * noise3(x, y, z);
            norm += amplitude;
            x = x * 2.03 + 17.1;
            y = y * 2.03 - 9.7;
            z = z * 2.03 + 5.3;
            amplitude *= 0.5;
        }
        return sum / norm;
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    private static void validate() {
        System.out.println("Black-hole validation");
        System.out.printf(Locale.ROOT, "  rs=%.3f  M=%.3f  b_critical=%.6f%n", RS, MASS, CRITICAL_B);

        double h2 = angularMomentumSq(0, 0, 10, 1, 0, 0);
        check("E3 angular momentum h^2 = 100", Math.abs(h2 - 100.0) < 1e-10,
                "h2=" + h2);

        Multivector p0 = P3.point(0, 1, 2);
        Multivector p1 = P3.point(0, -1, 2);
        Multivector hit = P3.meetPlaneLine(DISK_PLANE, P3.line(p0, p1));
        double[] hp = P3.pointCoords(hit);
        check("PGA plane ^ line hits y=0 at x=0,z=2",
                Math.abs(hp[0]) < 1e-9 && Math.abs(hp[1]) < 1e-9 && Math.abs(hp[2] - 2) < 1e-9,
                String.format(Locale.ROOT, "hit=(%.6f, %.6f, %.6f)", hp[0], hp[1], hp[2]));

        double deflection = deflection(50.0);
        double expected = 2.0 / 50.0; // rs/b weak-field limit
        double relative = Math.abs(deflection - expected) / expected;
        check("weak-field deflection within 5%", relative < 0.05,
                String.format(Locale.ROOT, "got=%.6f expected=%.6f rel=%.3f%%",
                        deflection, expected, 100.0 * relative));

        boolean captured2 = isCaptured(2.0);
        boolean captured3 = isCaptured(3.0);
        check("b=2.0 captured", captured2, "captured=" + captured2);
        check("b=3.0 escapes", !captured3, "captured=" + captured3);

        Shot shot = shoot(50.0, 1000.0, 20000);
        double endH2 = angularMomentumSq(shot.x, shot.y, shot.z, shot.vx, shot.vy, shot.vz);
        double conservation = Math.abs(endH2 - shot.h2) / shot.h2;
        check("h^2 conserved over a flyby", conservation < 1e-6,
                String.format(Locale.ROOT, "relative error=%.3e", conservation));

        System.out.println("validation passed");
    }

    private static double deflection(double impactParameter) {
        Shot shot = shoot(impactParameter, 1000.0, 20000);
        return Math.abs(Math.atan2(shot.vy, shot.vx));
    }

    private static boolean isCaptured(double impactParameter) {
        return shoot(impactParameter, 200.0, 20000).captured;
    }

    private record Shot(boolean captured, double x, double y, double z,
                        double vx, double vy, double vz, double h2, int steps) {}

    private static Shot shoot(double impactParameter, double startRadius, int maxSteps) {
        double x = -startRadius;
        double y = impactParameter;
        double z = 0.0;
        double vx = 1.0, vy = 0.0, vz = 0.0;
        double h2 = angularMomentumSq(x, y, z, vx, vy, vz);
        for (int i = 0; i < maxSteps; i++) {
            double r2 = x * x + y * y + z * z;
            double r = Math.sqrt(r2);
            if (r < EVENT_HORIZON) {
                return new Shot(true, x, y, z, vx, vy, vz, h2, i);
            }
            if (r > startRadius * 1.05 && (x * vx + y * vy + z * vz) > 0.0) {
                return new Shot(false, x, y, z, vx, vy, vz, h2, i);
            }
            double dt = Math.min(1.0, 0.012 + 0.035 * r + 0.020 * h2 / (r2 + 1.0));
            double k1 = -1.5 * h2 / (r2 * r2 * r);
            double ax1 = k1 * x, ay1 = k1 * y, az1 = k1 * z;
            double x2 = x + 0.5 * dt * vx, y2 = y + 0.5 * dt * vy, z2 = z + 0.5 * dt * vz;
            double vx2 = vx + 0.5 * dt * ax1, vy2 = vy + 0.5 * dt * ay1, vz2 = vz + 0.5 * dt * az1;
            double r22 = x2 * x2 + y2 * y2 + z2 * z2;
            double k2 = -1.5 * h2 / (r22 * r22 * Math.sqrt(r22));
            double ax2 = k2 * x2, ay2 = k2 * y2, az2 = k2 * z2;
            double x3 = x + 0.5 * dt * vx2, y3 = y + 0.5 * dt * vy2, z3 = z + 0.5 * dt * vz2;
            double vx3 = vx + 0.5 * dt * ax2, vy3 = vy + 0.5 * dt * ay2, vz3 = vz + 0.5 * dt * az2;
            double r23 = x3 * x3 + y3 * y3 + z3 * z3;
            double k3 = -1.5 * h2 / (r23 * r23 * Math.sqrt(r23));
            double ax3 = k3 * x3, ay3 = k3 * y3, az3 = k3 * z3;
            double x4 = x + dt * vx3, y4 = y + dt * vy3, z4 = z + dt * vz3;
            double vx4 = vx + dt * ax3, vy4 = vy + dt * ay3, vz4 = vz + dt * az3;
            double r24 = x4 * x4 + y4 * y4 + z4 * z4;
            double k4 = -1.5 * h2 / (r24 * r24 * Math.sqrt(r24));
            double ax4 = k4 * x4, ay4 = k4 * y4, az4 = k4 * z4;
            x += dt / 6.0 * (vx + 2.0 * vx2 + 2.0 * vx3 + vx4);
            y += dt / 6.0 * (vy + 2.0 * vy2 + 2.0 * vy3 + vy4);
            z += dt / 6.0 * (vz + 2.0 * vz2 + 2.0 * vz3 + vz4);
            vx += dt / 6.0 * (ax1 + 2.0 * ax2 + 2.0 * ax3 + ax4);
            vy += dt / 6.0 * (ay1 + 2.0 * ay2 + 2.0 * ay3 + ay4);
            vz += dt / 6.0 * (az1 + 2.0 * az2 + 2.0 * az3 + az4);
        }
        return new Shot(false, x, y, z, vx, vy, vz, h2, maxSteps);
    }

    private static double angularMomentumSq(double x, double y, double z,
                                            double vx, double vy, double vz) {
        Multivector position = E3.vec(x, y, z);
        Multivector velocity = E3.vec(vx, vy, vz);
        Multivector angularMomentum = position.op(velocity);
        // A spacelike bivector squares to -|L|^2 in Cl(3,0).
        return Math.max(0.0, -angularMomentum.gp(angularMomentum).scalarPart());
    }

    private static void check(String label, boolean ok, String detail) {
        System.out.printf(Locale.ROOT, "  [%s] %s (%s)%n", ok ? "ok" : "FAIL", label, detail);
        if (!ok) throw new AssertionError(label + ": " + detail);
    }

    // ---------------------------------------------------------------------
    // Small vector/tone helpers
    // ---------------------------------------------------------------------

    private static Multivector cross(Multivector a, Multivector b) {
        double[] p = E3.coords(a);
        double[] q = E3.coords(b);
        return E3.vec(
                p[1] * q[2] - p[2] * q[1],
                p[2] * q[0] - p[0] * q[2],
                p[0] * q[1] - p[1] * q[0]);
    }

    private static int pack(double red, double green, double blue) {
        int r = tone(red);
        int g = tone(green);
        int b = tone(blue);
        return (r << 16) | (g << 8) | b;
    }

    private static int tone(double value) {
        double mapped = 1.0 - Math.exp(-Math.max(0.0, value) * 1.15);
        mapped = Math.pow(mapped, 1.0 / 2.2);
        return (int) Math.round(255.0 * clamp(mapped, 0.0, 1.0));
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static double[] saturate(double[] rgb, double amount) {
        double luma = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
        return new double[]{
                Math.max(0.0, luma + (rgb[0] - luma) * amount),
                Math.max(0.0, luma + (rgb[1] - luma) * amount),
                Math.max(0.0, luma + (rgb[2] - luma) * amount)
        };
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Bilinear value noise in [0,1], deterministic across runs. */
    private static double noise2(double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);
        double a = hash(ix, iy, 7);
        double b = hash(ix + 1, iy, 7);
        double c = hash(ix, iy + 1, 7);
        double d = hash(ix + 1, iy + 1, 7);
        return (a + (b - a) * ux) * (1.0 - uy) + (c + (d - c) * ux) * uy;
    }
}
