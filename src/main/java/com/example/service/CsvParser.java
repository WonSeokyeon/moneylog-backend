package com.example.service;

import java.util.ArrayList;
import java.util.List;

// RFC 4180 최소 집합만 지원한다: 따옴표로 감싸기, 내부 따옴표는 ""로 이스케이프. 라이브러리를 쓰지 않는다(CLAUDE.md 5장).
public class CsvParser {

    private CsvParser() {
    }

    // split(",")을 쓰지 않는다 — 메모에 콤마가 들어가면 열이 밀린다. 따옴표 안/밖 상태를 추적해 분리한다.
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    // 값에 콤마·따옴표·줄바꿈이 있으면 전체를 따옴표로 감싸고 내부 따옴표를 ""로 이스케이프한다.
    public static String toCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // CSV 본문을 행 단위로 나눈다. 따옴표 쌍은 항상 짝수 번 나타나므로 만나는 족족 토글해도
    // 따옴표 안/밖 상태가 정확히 유지된다 — 그 상태가 true인 동안의 줄바꿈은 메모 안의 줄바꿈이지 행 경계가 아니다.
    public static List<String> splitRows(String content) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                row.append(c);
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                rows.add(row.toString());
                row.setLength(0);
            } else {
                row.append(c);
            }
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        return rows;
    }
}
