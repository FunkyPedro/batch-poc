package com.example.demo;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class UuidColumnRangePartitioner implements Partitioner {

    private static final int BUCKET_COUNT = 32;
    private static final String COLUMN_NAME = "identifier"; // Your UUID column name

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
       Map<String, ExecutionContext> partitions = new HashMap<>();
        
        // Generate 32 lexicographical ranges for UUID distribution
        String[] ranges = generateUuidRanges();
        
        for (int i = 0; i < BUCKET_COUNT; i++) {
            ExecutionContext context = new ExecutionContext();
            
            // Set the range boundaries for this partition
            if (i == 0) {
                // First partition: everything from start to first boundary
                context.putString("minValue", "00000000-0000-0000-0000-000000000000");
                context.putString("maxValue", ranges[i]);
            } else if (i == BUCKET_COUNT - 1) {
                // Last partition: from last boundary to end
                context.putString("minValue", ranges[i - 1]);
                context.putString("maxValue", "ffffffff-ffff-ffff-ffff-ffffffffffff");
            } else {
                // Middle partitions: from previous boundary to current boundary
                context.putString("minValue", ranges[i - 1]);
                context.putString("maxValue", ranges[i]);
            }
            
            context.putString("columnName", COLUMN_NAME);
            context.putInt("partitionNumber", i);
            
            partitions.put("partition" + i, context);
        }
        
        return partitions;
    }
     /**
     * Generate 31 boundary points to create 32 buckets
     * Each boundary represents 1/32, 2/32, 3/32, ... 31/32 of the UUID space
     */
    private String[] generateUuidRanges() {
        String[] ranges = new String[BUCKET_COUNT - 1]; // 31 boundaries for 32 buckets
        
        // UUID space is divided into 32 equal parts
        // Each part represents 1/32 of the total UUID space
        for (int i = 1; i < BUCKET_COUNT; i++) {
            ranges[i - 1] = calculateBoundary(i);
        }
        
        return ranges;
    }

    private String calculateBoundary(int bucketNumber) {
        // Calculate the boundary value as bucketNumber/32 of max UUID value
        // Max UUID: ffffffff-ffff-ffff-ffff-ffffffffffff
        // We'll work with the first 8 characters for simplicity and accuracy
        
        long maxValue = 0xFFFFFFFFL; // Maximum value for first 32 bits
        long bucketSize = maxValue / BUCKET_COUNT;
        long boundaryValue = bucketSize * bucketNumber;
        
        // Convert to hex string with proper padding
        String boundaryHex = String.format("%08x", boundaryValue);
        
        // Create UUID format: xxxxxxxx-0000-0000-0000-000000000000
        return String.format("%s-0000-0000-0000-000000000000", boundaryHex);
    }
   
    

}
