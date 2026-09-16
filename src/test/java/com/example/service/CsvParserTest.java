package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void 일반_라인은_콤마로_분리된다() {
        List<String> fields = CsvParser.parseLine("2026-09-14,지출,식비,12500,스타벅스,팀 미팅");

        assertThat(fields).containsExactly("2026-09-14", "지출", "식비", "12500", "스타벅스", "팀 미팅");
    }

    @Test
    void 따옴표로_감싼_필드_안의_콤마는_구분자로_취급되지_않는다() {
        List<String> fields = CsvParser.parseLine("2026-09-14,지출,식비,12500,스타벅스,\"팀 미팅, 결제\"");

        assertThat(fields).containsExactly("2026-09-14", "지출", "식비", "12500", "스타벅스", "팀 미팅, 결제");
    }

    @Test
    void 필드_안의_이스케이프된_겹따옴표는_한_글자로_치환된다() {
        List<String> fields = CsvParser.parseLine("\"그는 \"\"안녕\"\"이라 말했다\",100");

        assertThat(fields).containsExactly("그는 \"안녕\"이라 말했다", "100");
    }

    @Test
    void 줄_끝의_빈_필드도_보존된다() {
        List<String> fields = CsvParser.parseLine("2026-09-14,지출,식비,12500,,");

        assertThat(fields).containsExactly("2026-09-14", "지출", "식비", "12500", "", "");
    }

    @Test
    void toCsvField는_콤마_따옴표_줄바꿈이_없으면_그대로_반환한다() {
        assertThat(CsvParser.toCsvField("스타벅스")).isEqualTo("스타벅스");
        assertThat(CsvParser.toCsvField(null)).isEqualTo("");
    }

    @Test
    void toCsvField는_콤마가_있으면_따옴표로_감싼다() {
        assertThat(CsvParser.toCsvField("팀 미팅, 결제")).isEqualTo("\"팀 미팅, 결제\"");
    }

    @Test
    void toCsvField는_따옴표를_이스케이프하고_전체를_감싼다() {
        assertThat(CsvParser.toCsvField("그는 \"안녕\"이라 말했다")).isEqualTo("\"그는 \"\"안녕\"\"이라 말했다\"");
    }

    @Test
    void toCsvField는_줄바꿈이_있으면_따옴표로_감싼다() {
        assertThat(CsvParser.toCsvField("첫줄\n둘째줄")).isEqualTo("\"첫줄\n둘째줄\"");
    }

    @Test
    void 콤마_따옴표_줄바꿈이_섞인_값이_parseLine과_toCsvField를_왕복해도_원본과_일치한다() {
        String original = "메모, \"인용\" 그리고\n줄바꿈";
        String escaped = CsvParser.toCsvField(original);
        String line = escaped + ",다음필드";

        List<String> fields = CsvParser.parseLine(line);

        assertThat(fields.get(0)).isEqualTo(original);
        assertThat(fields.get(1)).isEqualTo("다음필드");
    }

    @Test
    void splitRows는_일반_줄바꿈으로_행을_나눈다() {
        List<String> rows = CsvParser.splitRows("1,2\r\n3,4\n5,6");

        assertThat(rows).containsExactly("1,2", "3,4", "5,6");
    }

    @Test
    void splitRows는_따옴표_안의_줄바꿈을_행_경계로_보지_않는다() {
        String content = "2026-09-14,지출,식비,12500,스타벅스,\"첫줄\n둘째줄\"\n2026-09-15,수입,급여,3000000,회사,";

        List<String> rows = CsvParser.splitRows(content);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo("2026-09-14,지출,식비,12500,스타벅스,\"첫줄\n둘째줄\"");
        assertThat(CsvParser.parseLine(rows.get(0)).get(5)).isEqualTo("첫줄\n둘째줄");
    }
}
