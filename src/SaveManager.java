import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SaveManager {
    private final MBC mbc;
    private final File saveFile;

    public SaveManager(MBC mbc, String romPath) {
        this.mbc = mbc;
        String savePath = romPath.substring(0, romPath.lastIndexOf('.')) + ".sav";
        this.saveFile = new File(savePath);
    }

    public void loadSave() {
        if (!saveFile.exists() || !(mbc instanceof Saveable saveable)) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(saveFile)) {
            byte[] ramData = fis.readAllBytes();
            saveable.loadRamData(ramData);
        } catch (IOException ignored) {
        }
    }

    public void save() {
        if (!(mbc instanceof Saveable saveable)) {
            return;
        }

        byte[] ramData = saveable.getRamData();
        if (ramData == null || ramData.length == 0) {
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            fos.write(ramData);
        } catch (IOException ignored) {
        }
    }
}