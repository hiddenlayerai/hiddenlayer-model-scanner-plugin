package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.lib.ModelScanner;
import com.hiddenlayer.api.models.scans.results.ScanReport;

public class ModelScannerWrapper implements ScannerService {

    private final ModelScanner modelScanner;

    public ModelScannerWrapper(ModelScanner modelScanner) {
        this.modelScanner = modelScanner;
    }

    @Override
    public ScanReport scanFolder(String modelName, String folderPath) {
        return modelScanner.scanFolder(modelName, folderPath);
    }
}
