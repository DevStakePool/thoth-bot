package com.devpool.thothBot.model.model.proposal;

import java.util.List;

public record ProposalContent(String title, String abstractText, List<String> authors) {
}
