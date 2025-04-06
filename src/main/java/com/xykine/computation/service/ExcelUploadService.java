package com.xykine.computation.service;

//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//import software.amazon.awssdk.core.sync.RequestBody;

//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ExcelUploadService {

    //@Autowired
    //private S3Client s3Client;

    //@Value("${s3.report.key-prefix}")
    private String reportKeyPrefix;

    //@Value("${aws.s3.bucket}") // Optional if you want bucket from config too
    private String bucketName;

    public String generateAndUploadExcel(List<String> headers, List<Map<String, Object>> dataRows, String fileName) throws IOException {
        //Workbook workbook = new XSSFWorkbook();
        //Sheet sheet = workbook.createSheet("Report");

        // Header row
        //Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
         //   headerRow.createCell(i).setCellValue(headers.get(i));
        }

        // Data rows
        for (int i = 0; i < dataRows.size(); i++) {
          //  Row row = sheet.createRow(i + 1);
            Map<String, Object> rowData = dataRows.get(i);
            for (int j = 0; j < headers.size(); j++) {
                Object value = rowData.get(headers.get(j));
            //    Cell cell = row.createCell(j);
                if (value instanceof Number) {
              //      cell.setCellValue(((Number) value).doubleValue());
                } else if (value != null) {
              //      cell.setCellValue(value.toString());
                } else {
              //      cell.setBlank();
                }
            }
        }

        // Upload to S3
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        //workbook.write(outputStream);
        //workbook.close();

        String s3Key = reportKeyPrefix + fileName;
        /*
        s3Client.putObject(
               PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .build(),
                RequestBody.fromBytes(outputStream.toByteArray())
        );
         */
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, s3Key);
    }
}
