package com.bw.jPdfTool.model;

public final class MergeOptions {

    /**
     * 1-based Start Page Number for merged pages.
     */
    public int startPageNb = -1;

    /**
     * Size of inserted segments.
     */
    public int segmentLength = -1;

    /**
     * Number of original pages between segments.
     */
    public int gapLength = -1;

}
