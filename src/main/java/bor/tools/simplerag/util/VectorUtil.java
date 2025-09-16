package bor.tools.simplerag.util;

import java.util.Arrays;

/**
 * Utilities for vector operations, particularly for embedding normalization and similarity calculations.
 *
 * Based on the VecUtil class from the reference implementation,
 * adapted for JSimpleRag's requirements.
 */
public class VectorUtil {

    /**
     * Normalizes a vector to unit length (L2 normalization).
     *
     * @param vector the vector to normalize
     * @return the normalized vector
     * @throws IllegalArgumentException if vector is null or empty
     */
    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }

        // Calculate the L2 norm (Euclidean norm)
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        // If the norm is 0, return the original vector (avoid division by zero)
        if (norm == 0.0) {
            return Arrays.copyOf(vector, vector.length);
        }

        // Normalize each component
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }

        return normalized;
    }

    /**
     * Calculates cosine similarity between two vectors.
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @return cosine similarity value between -1 and 1
     * @throws IllegalArgumentException if vectors are null, empty, or have different dimensions
     */
    public static double cosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        if (vector1.length == 0) {
            throw new IllegalArgumentException("Vectors cannot be empty");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0; // If either vector is zero, similarity is 0
        }

        return dotProduct / (norm1 * norm2);
    }

    /**
     * Calculates Euclidean distance between two vectors.
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @return Euclidean distance
     * @throws IllegalArgumentException if vectors are null, empty, or have different dimensions
     */
    public static double euclideanDistance(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        if (vector1.length == 0) {
            throw new IllegalArgumentException("Vectors cannot be empty");
        }

        double sum = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            double diff = vector1[i] - vector2[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }

    /**
     * Calculates dot product between two vectors.
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @return dot product
     * @throws IllegalArgumentException if vectors are null, empty, or have different dimensions
     */
    public static double dotProduct(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        if (vector1.length == 0) {
            throw new IllegalArgumentException("Vectors cannot be empty");
        }

        double result = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            result += vector1[i] * vector2[i];
        }

        return result;
    }

    /**
     * Calculates the L2 norm (Euclidean norm) of a vector.
     *
     * @param vector the vector
     * @return the L2 norm
     * @throws IllegalArgumentException if vector is null or empty
     */
    public static double norm(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }

        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }

        return Math.sqrt(sum);
    }

    /**
     * Checks if a vector is normalized (has unit length).
     *
     * @param vector the vector to check
     * @param tolerance tolerance for floating point comparison
     * @return true if the vector is normalized within the given tolerance
     */
    public static boolean isNormalized(float[] vector, double tolerance) {
        if (vector == null || vector.length == 0) {
            return false;
        }

        double norm = norm(vector);
        return Math.abs(norm - 1.0) <= tolerance;
    }

    /**
     * Checks if a vector is normalized with default tolerance (1e-6).
     *
     * @param vector the vector to check
     * @return true if the vector is normalized
     */
    public static boolean isNormalized(float[] vector) {
        return isNormalized(vector, 1e-6);
    }

    /**
     * Resizes a vector to the target dimension.
     * If the target dimension is larger, pads with zeros.
     * If smaller, truncates the vector.
     *
     * @param vector the original vector
     * @param targetDimension the target dimension
     * @return the resized vector
     * @throws IllegalArgumentException if vector is null or targetDimension is non-positive
     */
    public static float[] resize(float[] vector, int targetDimension) {
        if (vector == null) {
            throw new IllegalArgumentException("Vector cannot be null");
        }
        if (targetDimension <= 0) {
            throw new IllegalArgumentException("Target dimension must be positive");
        }

        if (vector.length == targetDimension) {
            return Arrays.copyOf(vector, vector.length);
        }

        float[] resized = new float[targetDimension];
        int copyLength = Math.min(vector.length, targetDimension);
        System.arraycopy(vector, 0, resized, 0, copyLength);

        return resized;
    }

    /**
     * Adds two vectors element-wise.
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @return the sum vector
     * @throws IllegalArgumentException if vectors are null, empty, or have different dimensions
     */
    public static float[] add(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        float[] result = new float[vector1.length];
        for (int i = 0; i < vector1.length; i++) {
            result[i] = vector1[i] + vector2[i];
        }

        return result;
    }

    /**
     * Subtracts two vectors element-wise (vector1 - vector2).
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @return the difference vector
     * @throws IllegalArgumentException if vectors are null, empty, or have different dimensions
     */
    public static float[] subtract(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        float[] result = new float[vector1.length];
        for (int i = 0; i < vector1.length; i++) {
            result[i] = vector1[i] - vector2[i];
        }

        return result;
    }

    /**
     * Multiplies a vector by a scalar.
     *
     * @param vector the vector
     * @param scalar the scalar value
     * @return the scaled vector
     * @throws IllegalArgumentException if vector is null
     */
    public static float[] scale(float[] vector, double scalar) {
        if (vector == null) {
            throw new IllegalArgumentException("Vector cannot be null");
        }

        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] * scalar);
        }

        return result;
    }

    /**
     * Checks if two vectors are equal within a given tolerance.
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @param tolerance tolerance for floating point comparison
     * @return true if vectors are equal within tolerance
     */
    public static boolean equals(float[] vector1, float[] vector2, double tolerance) {
        if (vector1 == vector2) {
            return true;
        }
        if (vector1 == null || vector2 == null) {
            return false;
        }
        if (vector1.length != vector2.length) {
            return false;
        }

        for (int i = 0; i < vector1.length; i++) {
            if (Math.abs(vector1[i] - vector2[i]) > tolerance) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if two vectors are equal with default tolerance (1e-6).
     *
     * @param vector1 first vector
     * @param vector2 second vector
     * @return true if vectors are equal
     */
    public static boolean equals(float[] vector1, float[] vector2) {
        return equals(vector1, vector2, 1e-6);
    }

    /**
     * Creates a zero vector of the specified dimension.
     *
     * @param dimension the dimension of the vector
     * @return a zero vector
     * @throws IllegalArgumentException if dimension is non-positive
     */
    public static float[] zeros(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive");
        }
        return new float[dimension];
    }

    /**
     * Creates a random vector with values between -1 and 1.
     *
     * @param dimension the dimension of the vector
     * @return a random vector
     * @throws IllegalArgumentException if dimension is non-positive
     */
    public static float[] random(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive");
        }

        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) (Math.random() * 2.0 - 1.0); // Random between -1 and 1
        }

        return vector;
    }

    /**
     * Creates a normalized random vector.
     *
     * @param dimension the dimension of the vector
     * @return a normalized random vector
     * @throws IllegalArgumentException if dimension is non-positive
     */
    public static float[] randomNormalized(int dimension) {
        return normalize(random(dimension));
    }
}