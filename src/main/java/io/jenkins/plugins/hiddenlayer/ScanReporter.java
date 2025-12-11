package io.jenkins.plugins.hiddenlayer;

import com.hiddenlayer.api.models.scans.results.ScanReport;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class ScanReporter {

    public static String summarizeScan(ScanReport scanReport) {
        ScanReport.Inventory inventory = scanReport.inventory();
        String modelName = inventory.modelName();
        String modelVersion = inventory.modelVersion().orElse("unknown");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        Optional<OffsetDateTime> endTimeOpt = scanReport.endTime();
        String scanEndTime = endTimeOpt.map(dt -> dt.format(formatter)).orElse("unknown");

        StringBuilder sb = new StringBuilder(
                String.format("Scan results for model \"%s\", version %s:%n", modelName, modelVersion));
        addLine(sb, "Status", scanReport.status().toString());
        addLine(sb, "Severity", scanReport.severity().map(Object::toString).orElse("unknown"));
        addLine(sb, "End time", scanEndTime);
        addLine(sb, "Scanner version", scanReport.version());
        addLine(sb, "Console scan link", getScanResultsUrl(scanReport));
        return sb.toString();
    }

    private static void addLine(StringBuilder sb, String key, String value) {
        sb.append(String.format("%s: %s%n", key, value));
    }

    private static String getScanResultsUrl(ScanReport scanReport) {
        String modelId = scanReport.inventory().modelId();
        return "https://console.us.hiddenlayer.ai/model-details/" + modelId + "/scans/" + scanReport.scanId();
    }
}
