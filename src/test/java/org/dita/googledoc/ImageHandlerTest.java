package org.dita.googledoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageHandlerTest {

    @Test
    void imageInfo_holdsValues() {
        ImageHandler.ImageInfo info = new ImageHandler.ImageInfo(
            "https://example.com/img.png", 200.0, 100.0);
        assertEquals("https://example.com/img.png", info.uri());
        assertEquals(200.0, info.widthPt());
        assertEquals(100.0, info.heightPt());
    }

    @Test
    void calculateDimensions_scalesDown(@TempDir Path tempDir) throws IOException {
        BufferedImage img = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);
        Path imgPath = tempDir.resolve("wide.png");
        ImageIO.write(img, "png", imgPath.toFile());

        double[] dims = ImageHandler.calculateDimensions(imgPath.toString(), 468.0);

        assertEquals(468.0, dims[0], 0.1);
        assertEquals(234.0, dims[1], 0.1);
    }

    @Test
    void calculateDimensions_noScaleWhenSmall(@TempDir Path tempDir) throws IOException {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Path imgPath = tempDir.resolve("small.png");
        ImageIO.write(img, "png", imgPath.toFile());

        double[] dims = ImageHandler.calculateDimensions(imgPath.toString(), 468.0);

        assertEquals(150.0, dims[0], 0.1);
        assertEquals(75.0, dims[1], 0.1);
    }
}
