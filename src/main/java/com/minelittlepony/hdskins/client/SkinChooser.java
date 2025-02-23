package com.minelittlepony.hdskins.client;

import com.minelittlepony.hdskins.client.gui.FileSaverScreen;
import com.minelittlepony.hdskins.client.gui.FileSelectorScreen;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

public class SkinChooser {

    public static final int MAX_SKIN_DIMENSION = 1024;

    public static final String[] EXTENSIONS = new String[]{"png", "PNG"};

    public static final Text ERR_UNREADABLE = Text.translatable("hdskins.error.unreadable");
    public static final Text ERR_EXT = Text.translatable("hdskins.error.ext");
    public static final Text ERR_OPEN = Text.translatable("hdskins.error.open");
    public static final Text ERR_INVALID_TOO_LARGE = Text.translatable("hdskins.error.invalid.too_large");
    public static final Text ERR_INVALID_SHAPE = Text.translatable("hdskins.error.invalid.shape");
    public static final Text ERR_INVALID_POWER_OF_TWO = Text.translatable("hdskins.error.invalid.power_of_two");
    public static final Text ERR_INVALID = Text.translatable("hdskins.error.invalid");

    public static final Text MSG_CHOOSE = Text.translatable("hdskins.choose");

    private static boolean isPowerOfTwo(int number) {
        return number != 0 && (number & number - 1) == 0;
    }

    @Nullable
    private FileDialog openFileThread;

    private final SkinUploader uploader;

    private final List<Function<NativeImage, Text>> validators = new ArrayList<>();

    private volatile Text status = MSG_CHOOSE;

    public SkinChooser(SkinUploader uploader) {
        this.uploader = uploader;
        addImageValidation(this::acceptsSkinDimensions);
    }

    public void addImageValidation(Function<NativeImage, Text> validator) {
        validators.add(validator);
    }

    public boolean pickingInProgress() {
        return openFileThread != null;
    }

    public Text getStatus() {
        return status;
    }

    public void openBrowsePNG(String title) {
        openFileThread = new FileSelectorScreen(title)
                .filter(".png", "PNG Files (*.png)")
                .andThen((file, success) -> {
            openFileThread = null;

            if (success) {
                selectFile(file);
            }
        }).launch();
    }

    public void openSavePNG(String title, String filename) {
        openFileThread = new FileSaverScreen(title, filename)
                .filter(".png", "PNG Files (*.png)")
                .andThen((file, success) -> {
            openFileThread = null;

            if (success) {
                uploader.getServerTexture().ifPresent(texture -> {
                    try (InputStream response = texture.openStream()) {
                        Files.copy(response, file);
                    } catch (IOException e) {
                        LogManager.getLogger().error("Failed to save remote skin.", e);
                    }
                });
            }
        }).launch();
    }

    public void selectFile(Path skinFile) {
        status = evaluateAndSelect(skinFile);
    }

    private Text evaluateAndSelect(Path skinFile) {
        if (!Files.exists(skinFile)) {
            return ERR_UNREADABLE;
        }

        if (!FilenameUtils.isExtension(skinFile.getFileName().toString(), EXTENSIONS)) {
            return ERR_EXT;
        }

        try (InputStream in = Files.newInputStream(skinFile)) {
            NativeImage chosenImage = NativeImage.read(in);

            return validators.stream()
                .map(f -> f.apply(chosenImage))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> {
                    uploader.setLocalSkin(skinFile);

                    return MSG_CHOOSE;
                });
        } catch (IOException e) {
            HDSkins.LOGGER.error("Exception occured whilst loading image file {}.", skinFile, e);
        }

        return ERR_OPEN;
    }

    @Nullable
    protected Text acceptsSkinDimensions(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        if (!isPowerOfTwo(w)) {
            return ERR_INVALID_POWER_OF_TWO;
        }
        if (w > MAX_SKIN_DIMENSION) {
            return ERR_INVALID_TOO_LARGE;
        }
        if (!(w == h || w == h * 2)) {
            return ERR_INVALID_SHAPE;
        }
        return null;
    }
}
