/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package org.fcm.gcm.mcs.gcm.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

import static org.fcm.gcm.mcs.model.JsonService.OBJECT_MAPPER;

public class FileNotificationIdPersistor extends AbstractNotificationIdPersistor {
    private final File file;
    private boolean initialized;

    public FileNotificationIdPersistor(File file) {
        this.file = file;
    }

    @Override
    public void load() {
        try {
            //noinspection unchecked
            addAll(OBJECT_MAPPER.readValue(file, HashSet.class));
        } catch (IOException e) {
            e.printStackTrace();
        }
        initialized = true;
    }

    @Override
    public void save() {
        try {
            if (initialized)
                OBJECT_MAPPER.writeValue(file, new ArrayList<>(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
