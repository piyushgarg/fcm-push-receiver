/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package org.fcm.gcm.mcs.gcm.persistence;

import java.util.HashSet;

public abstract class AbstractNotificationIdPersistor extends HashSet<String> {
    @Override
    public boolean add(String s) {
        boolean result = super.add(s);
        save();
        return result;
    }

    @Override
    public boolean remove(Object o) {
        boolean result = super.remove(o);
        save();
        return result;
    }

    public abstract void load();

    public abstract void save();

}
