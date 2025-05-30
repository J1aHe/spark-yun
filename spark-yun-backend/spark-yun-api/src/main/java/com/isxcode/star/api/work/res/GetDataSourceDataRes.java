package com.isxcode.star.api.work.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetDataSourceDataRes {

    private List<String> columns;

    private List<List<String>> rows;
}
