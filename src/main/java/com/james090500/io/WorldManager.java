package com.james090500.io;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WorldManager {

    /**
     * List all worlds
     * @return
     */
    public static List<String> getWorlds() {
        List<String> worlds = new ArrayList<>();

        File path = new File("worlds");
        File[] folders = path.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                worlds.add(folder.getName());
            }
        }

        return worlds;
    }
}
