package dev.kettu.hyangsang.tools

import com.google.gson.annotations.SerializedName

data class LexicalResource(
    @SerializedName("LexicalResource")
    val lexicalResource: LexicalResourceX
)

data class LexicalResourceX(
    @SerializedName("dtdVersion")
    val dtdVersion: String,
    @SerializedName("GlobalInformation")
    val globalInformation: GlobalInformation,
    @SerializedName("Lexicon")
    val lexicon: Lexicon
)

data class GlobalInformation(
    @SerializedName("feat")
    val feat: List<Feat>
)

data class Lexicon(
    @SerializedName("feat")
    val feat: Feat,
    @SerializedName("LexicalEntry")
    val lexicalEntry: List<LexicalEntry>
)

data class Feat(
    @SerializedName("att")
    val att: String,
    @SerializedName("val")
    val valX: String
)

data class LexicalEntry(
    @SerializedName("att")
    val att: String,
    @SerializedName("feat")
    val feat: List<Feat>,
    @SerializedName("Lemma")
    val lemma: List<Lemma>,
    @SerializedName("RelatedForm")
    val relatedForm: List<RelatedForm>,
    @SerializedName("Sense")
    val sense: List<Sense>,
    @SerializedName("val")
    val valX: String,
    @SerializedName("WordForm")
    val wordForm: List<WordForm>?
)

data class Lemma(
    @SerializedName("feat")
    val feat: Feat
)

data class RelatedForm(
    @SerializedName("feat")
    val feat: List<Feat>
)

data class Sense(
    @SerializedName("att")
    val att: String,
    @SerializedName("Equivalent")
    val equivalent: List<Equivalent>?,
    @SerializedName("feat")
    val feat: List<Feat>,
    @SerializedName("SenseExample")
    val senseExample: List<SenseExample>?,
    @SerializedName("val")
    val valX: String
)

data class WordForm(
    @SerializedName("feat")
    val feat: List<Feat>
)

data class Equivalent(
    @SerializedName("feat")
    val feat: List<Feat>
)

data class SenseExample(
    @SerializedName("feat")
    val feat: List<Feat>
)


