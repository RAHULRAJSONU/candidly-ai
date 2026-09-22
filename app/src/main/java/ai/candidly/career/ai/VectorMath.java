package ai.candidly.career.ai;

import java.util.List;

public final class VectorMath {

    private VectorMath() {
    }

    /** Element-wise mean of one or more same-dimension vectors, or null if the list is empty. */
    public static float[] meanPool(List<float[]> vectors) {
        if (vectors.isEmpty()) {
            return null;
        }
        int dim = vectors.get(0).length;
        float[] pooled = new float[dim];
        for (float[] v : vectors) {
            for (int i = 0; i < dim; i++) {
                pooled[i] += v[i] / vectors.size();
            }
        }
        return pooled;
    }

    /** Cosine similarity in [-1, 1]; 0 if either vector has zero magnitude. */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch (%d vs %d) - are these from the same embedding model/version?"
                            .formatted(a.length, b.length));
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
