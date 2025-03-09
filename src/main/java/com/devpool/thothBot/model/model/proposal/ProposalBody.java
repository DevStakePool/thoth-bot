package com.devpool.thothBot.model.model.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProposalBody(String title,
                           @JsonProperty("abstract") String abstractValue) {
}
