package com.wajub;

import java.util.List;
import java.util.Map;

/** Holds a list response with optional pagination meta. */
public record ListResult(List<Map<String, Object>> data, Map<String, Object> meta) {}
