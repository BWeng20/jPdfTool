package com.bw.jPdfTool.model;

public interface PdfLoadWorker {

    void execute();

    void cancel();

    boolean isFinished();

}
