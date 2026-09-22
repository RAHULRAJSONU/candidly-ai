package ai.candidly.career.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VectorMathTest {

    @Test
    void identicalVectorsHaveSimilarityOne() {
        float[] a = { 1f, 2f, 3f };
        assertThat(VectorMath.cosineSimilarity(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void orthogonalVectorsHaveSimilarityZero() {
        float[] a = { 1f, 0f };
        float[] b = { 0f, 1f };
        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void mismatchedDimensionsThrow() {
        float[] a = { 1f, 2f };
        float[] b = { 1f, 2f, 3f };
        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroVectorYieldsZeroNotNaN() {
        float[] zero = { 0f, 0f };
        float[] other = { 1f, 1f };
        assertThat(VectorMath.cosineSimilarity(zero, other)).isZero();
    }
}
