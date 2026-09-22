package ai.candidly.career.domain;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.usertype.UserType;

import com.pgvector.PGvector;

/**
 * Maps a Java {@code float[]} to a native Postgres {@code vector(n)} column (pgvector
 * extension), so embeddings are stored and indexed by Postgres/pgvector directly rather
 * than serialized as opaque text (docs/01 §4.1-4.2 - pin one embedding model, size the
 * column to it, index with HNSW). Retrieval-time ANN search (`<=>` operator) is done via
 * plain JDBC in {@code retrieval.VectorSearchService}, not through this type - Hibernate
 * doesn't have a clean way to bind a `vector` literal into a native-query `ORDER BY`
 * expression, and a raw JdbcTemplate call is simpler and more honest about what's
 * actually a hot-path SQL operation.
 *
 * <p>{@code com.pgvector:pgvector} supplies {@link PGvector} (a {@code PGobject}) but no
 * Hibernate {@code UserType} of its own, so this class is the glue. Reading back a
 * `vector` column: pgjdbc returns an unrecognized column type as a {@code PGobject}
 * whose {@code getValue()} is the vector's text form (e.g. {@code "[0.1,0.2,...]"}) even
 * without registering the type on the connection, which is why {@code PGvector(String)}
 * parses directly off {@code rs.getString(...)}.
 */
public class PgVectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        String text = rs.getString(position);
        if (text == null) {
            return null;
        }
        return new PGvector(text).toArray();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, new PGvector(value));
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public java.io.Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(java.io.Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
