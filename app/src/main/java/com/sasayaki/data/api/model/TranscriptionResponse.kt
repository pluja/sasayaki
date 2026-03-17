package com.sasayaki.data.api.model

import com.google.gson.annotations.SerializedName

data class TranscriptionResponse(
    @SerializedName("text") val text: String
)
