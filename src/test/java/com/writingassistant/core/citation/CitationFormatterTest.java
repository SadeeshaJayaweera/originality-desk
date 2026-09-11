package com.writingassistant.core.citation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitationFormatterTest {

    private SourceQuote bookSource;
    private SourceQuote journalSource;
    private SourceQuote websiteSource;
    private SourceQuote multiAuthorSource;

    @BeforeEach
    void setUp() {
        // 1. Book Source
        bookSource = new SourceQuote(
                "A reliable way to make people believe in falsehoods is frequent repetition, because familiarity is not easily distinguished from truth.",
                "Daniel Kahneman",
                "2011",
                "Thinking, Fast and Slow",
                "Farrar, Straus and Giroux",
                "62",
                null,
                SourceType.BOOK
        );

        // 2. Journal Source (2 Authors)
        journalSource = new SourceQuote(
                "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks.",
                "Ashish Vaswani and Noam Shazeer",
                "2017",
                "Attention Is All You Need",
                "Advances in Neural Information Processing Systems",
                "5998-6008",
                "https://doi.org/10.48550/arXiv.1706.03762",
                SourceType.JOURNAL
        );
        journalSource.setVolume("30");
        journalSource.setIssue("1");

        // 3. Website Source
        websiteSource = new SourceQuote(
                "Consistency is one of the most powerful usability principles.",
                "Jakob Nielsen",
                "2020",
                "10 Usability Heuristics for User Interface Design",
                "Nielsen Norman Group",
                null,
                "https://www.nngroup.com/articles/ten-usability-heuristics/",
                SourceType.WEBSITE
        );

        // 4. Multi-Author Source (3+ Authors)
        multiAuthorSource = new SourceQuote(
                "Deep residual networks are easier to optimize and can gain accuracy from considerably increased depth.",
                "Kaiming He, Xiangyu Zhang, Shaoqing Ren, and Jian Sun",
                "2016",
                "Deep Residual Learning for Image Recognition",
                "IEEE Conference on Computer Vision and Pattern Recognition",
                "770-778",
                "https://doi.org/10.1109/CVPR.2016.90",
                SourceType.JOURNAL
        );
        multiAuthorSource.setVolume("2016");
    }

    // =========================================================================
    // APA 7th Edition Tests
    // =========================================================================

    @Test
    @DisplayName("APA 7th: In-text citations for single, dual, and 3+ authors with page numbers")
    void testApaInText() {
        // Single author with page
        assertEquals("(Kahneman, 2011, p. 62)", CitationFormatter.formatInText(bookSource, CitationStyle.APA));

        // Two authors with page range (APA uses '&' and 'pp.')
        assertEquals("(Vaswani & Shazeer, 2017, pp. 5998-6008)",
                CitationFormatter.formatInText(journalSource, CitationStyle.APA));

        // Website without page
        assertEquals("(Nielsen, 2020)", CitationFormatter.formatInText(websiteSource, CitationStyle.APA));

        // 3+ authors (APA uses 'et al.' even on first citation)
        assertEquals("(He et al., 2016, pp. 770-778)",
                CitationFormatter.formatInText(multiAuthorSource, CitationStyle.APA));
    }

    @Test
    @DisplayName("APA 7th: Reference list entries for Book, Journal, and Website")
    void testApaBibliography() {
        // Book: Author, A. A. (Year). *Title*. Publisher.
        String bookBib = CitationFormatter.formatBibliographyEntry(bookSource, CitationStyle.APA);
        assertEquals("Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.", bookBib);

        // Journal: Author, A. A., & Author, B. B. (Year). Title. *Periodical*, *vol*(issue), pp. https://doi...
        String journalBib = CitationFormatter.formatBibliographyEntry(journalSource, CitationStyle.APA);
        assertEquals("Vaswani, A., & Shazeer, N. (2017). Attention Is All You Need. *Advances in Neural Information Processing Systems*, *30*(1), 5998-6008. https://doi.org/10.48550/arXiv.1706.03762", journalBib);

        // Website: Author, A. A. (Year). *Title*. Website. URL
        String webBib = CitationFormatter.formatBibliographyEntry(websiteSource, CitationStyle.APA);
        assertEquals("Nielsen, J. (2020). *10 Usability Heuristics for User Interface Design*. Nielsen Norman Group. https://www.nngroup.com/articles/ten-usability-heuristics/", webBib);
    }

    // =========================================================================
    // MLA 9th Edition Tests
    // =========================================================================

    @Test
    @DisplayName("MLA 9th: In-text citations for single, dual, and 3+ authors")
    void testMlaInText() {
        // Single author with page (MLA format: (Author Page))
        assertEquals("(Kahneman 62)", CitationFormatter.formatInText(bookSource, CitationStyle.MLA));

        // Two authors with page (MLA format: (Author and Author Page))
        assertEquals("(Vaswani and Shazeer 5998-6008)",
                CitationFormatter.formatInText(journalSource, CitationStyle.MLA));

        // Website without page
        assertEquals("(Nielsen)", CitationFormatter.formatInText(websiteSource, CitationStyle.MLA));

        // 3+ authors
        assertEquals("(He et al. 770-778)",
                CitationFormatter.formatInText(multiAuthorSource, CitationStyle.MLA));
    }

    @Test
    @DisplayName("MLA 9th: Works Cited entries for Book, Journal, and Website")
    void testMlaBibliography() {
        // Book: Author, First. *Title of Book*. Publisher, Year.
        String bookBib = CitationFormatter.formatBibliographyEntry(bookSource, CitationStyle.MLA);
        assertEquals("Kahneman, Daniel. *Thinking, Fast and Slow*. Farrar, Straus and Giroux, 2011.", bookBib);

        // Journal: Author, First, and First Author. "Title." *Journal*, vol. X, no. Y, Year, pp. XX. URL.
        String journalBib = CitationFormatter.formatBibliographyEntry(journalSource, CitationStyle.MLA);
        assertEquals("Vaswani, Ashish, and Noam Shazeer. \"Attention Is All You Need.\" *Advances in Neural Information Processing Systems*, vol. 30, no. 1, 2017, pp. 5998-6008. https://doi.org/10.48550/arXiv.1706.03762.", journalBib);

        // Website: Author, First. "Title." *Website*, Year, URL.
        String webBib = CitationFormatter.formatBibliographyEntry(websiteSource, CitationStyle.MLA);
        assertEquals("Nielsen, Jakob. \"10 Usability Heuristics for User Interface Design.\" *Nielsen Norman Group*, 2020. https://www.nngroup.com/articles/ten-usability-heuristics/.", webBib);
    }

    // =========================================================================
    // Chicago 17th Edition Tests
    // =========================================================================

    @Test
    @DisplayName("Chicago 17th (Author-Date): In-text citations for single, dual, and 3+ authors")
    void testChicagoInText() {
        // Single author with page (Chicago: (Author Year, Page))
        assertEquals("(Kahneman 2011, 62)", CitationFormatter.formatInText(bookSource, CitationStyle.CHICAGO));

        // Two authors with page (Chicago: (Author and Author Year, Page))
        assertEquals("(Vaswani and Shazeer 2017, 5998-6008)",
                CitationFormatter.formatInText(journalSource, CitationStyle.CHICAGO));

        // Website without page
        assertEquals("(Nielsen 2020)", CitationFormatter.formatInText(websiteSource, CitationStyle.CHICAGO));

        // 3+ authors (Chicago uses et al. for 4+)
        assertEquals("(He et al. 2016, 770-778)",
                CitationFormatter.formatInText(multiAuthorSource, CitationStyle.CHICAGO));
    }

    @Test
    @DisplayName("Chicago 17th (Author-Date): Reference list entries for Book, Journal, and Website")
    void testChicagoBibliography() {
        // Book: Author, First. Year. *Title*. Publisher.
        String bookBib = CitationFormatter.formatBibliographyEntry(bookSource, CitationStyle.CHICAGO);
        assertEquals("Kahneman, Daniel. 2011. *Thinking, Fast and Slow*. Farrar, Straus and Giroux.", bookBib);

        // Journal: Author. Year. "Title." *Journal* vol (issue): pages. URL.
        String journalBib = CitationFormatter.formatBibliographyEntry(journalSource, CitationStyle.CHICAGO);
        assertEquals("Vaswani, Ashish, and Noam Shazeer. 2017. \"Attention Is All You Need.\" *Advances in Neural Information Processing Systems* 30 (1): 5998-6008. https://doi.org/10.48550/arXiv.1706.03762.", journalBib);

        // Website: Author. Year. "Title." Website. URL.
        String webBib = CitationFormatter.formatBibliographyEntry(websiteSource, CitationStyle.CHICAGO);
        assertEquals("Nielsen, Jakob. 2020. \"10 Usability Heuristics for User Interface Design.\" Nielsen Norman Group. https://www.nngroup.com/articles/ten-usability-heuristics/.", webBib);
    }
}
