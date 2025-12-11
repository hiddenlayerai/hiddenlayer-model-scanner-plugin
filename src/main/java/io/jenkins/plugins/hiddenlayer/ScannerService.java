package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.models.scans.results.ScanReport;

public interface ScannerService {
    ScanReport scanFolder(String modelName, String folderPath);
}
