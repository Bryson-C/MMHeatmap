package org.bc.MMheatmap;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;

/**
 * A class which extends "DynmapCommonAPIListener" to allow for the user to get access to the
 * DynmapCommonAPI object for later use.
 *
 * @author BC/Exo
 */
public class DynmapListener extends DynmapCommonAPIListener {
    private static DynmapCommonAPI api;

    /**
     * The method is given a DynmapCommonApi object on dynmap enable. Save this to a static
     * variable, and allow the user to get the Api object with <code>DynmapListener::getApi()</code>
     *
     * @param dynmapCommonAPI Passed into the function on startup of the dynmap API
     */
    @Override
    public void apiEnabled(DynmapCommonAPI dynmapCommonAPI) {
        api = dynmapCommonAPI;
    }

    /**
     * Allows the user to get the DynmapCommonAPI created when the class is initialized.
     *
     * @return Returns the DynmapCommonAPI object created on the creation of this class
     */
    public static DynmapCommonAPI getApi() {
        return api;
    }
}
