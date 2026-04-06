package foundation.odin;

import java.nio.file.*;

final class GoldenTestHelper {
    private GoldenTestHelper() {}

    static Path findGoldenDir() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path golden = cwd.resolve("../golden").normalize();
        if (Files.isDirectory(golden)) return golden;
        golden = cwd.resolve("../../golden").normalize();
        if (Files.isDirectory(golden)) return golden;
        golden = Paths.get("C:/dev/odin/sdk/golden");
        if (Files.isDirectory(golden)) return golden;
        throw new RuntimeException("Cannot find sdk/golden/ directory from " + cwd);
    }
}
