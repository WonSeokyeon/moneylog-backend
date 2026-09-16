package com.example.dto;

import java.util.List;

public record CsvImportResult(int imported, int failed, List<CsvImportResult.CsvImportError> errors) {

    public record CsvImportError(int line, String reason) {
    }
}
