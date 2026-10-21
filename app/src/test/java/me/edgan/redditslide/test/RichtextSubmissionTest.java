package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.edgan.redditslide.util.RichtextSubmission;
import org.junit.Test;

/** Tests for the submit-reply fullname normalisation in {@link RichtextSubmission}. */
public class RichtextSubmissionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    public void selfPostReply_prefersPrefixedName() throws Exception {
        assertEquals(
                "t3_1abcde",
                RichtextSubmission.submissionFullname(
                        node(
                                "{\"json\":{\"errors\":[],\"data\":{\"id\":\"1abcde\","
                                        + "\"name\":\"t3_1abcde\",\"url\":\"https://www.reddit"
                                        + ".com/r/test/comments/1abcde/title/\"}}}")));
    }

    @Test
    public void bareId_getsPrefixed() throws Exception {
        assertEquals(
                "t3_1abcde",
                RichtextSubmission.submissionFullname(
                        node("{\"json\":{\"errors\":[],\"data\":{\"id\":\"1abcde\"}}}")));
    }

    @Test
    public void galleryReply_keepsPrefixedId() throws Exception {
        assertEquals(
                "t3_1abcde",
                RichtextSubmission.submissionFullname(
                        node(
                                "{\"json\":{\"errors\":[],\"data\":{\"id\":\"t3_1abcde\","
                                        + "\"url\":\"https://www.reddit.com/r/test/comments/"
                                        + "1abcde/title/\"}}}")));
    }

    @Test
    public void nestedName_isFound() throws Exception {
        assertEquals(
                "t3_1abcde",
                RichtextSubmission.submissionFullname(
                        node(
                                "{\"json\":{\"errors\":[],\"data\":{\"things\":[{\"data\":"
                                        + "{\"name\":\"t3_1abcde\"}}]}}}")));
    }

    @Test
    public void noSubmission_isNull() throws Exception {
        assertNull(
                RichtextSubmission.submissionFullname(
                        node("{\"json\":{\"errors\":[],\"data\":{}}}")));
        assertNull(RichtextSubmission.submissionFullname(node("{}")));
    }
}
