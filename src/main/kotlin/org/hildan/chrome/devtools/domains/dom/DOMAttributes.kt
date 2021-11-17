package org.hildan.chrome.devtools.domains.dom

import java.nio.charset.Charset
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Converts a list of attributes given as `[key1, value1, key2, value2]` into a [DOMAttributes] instance.
 *
 * Attributes that are present on an HTML element without a value (e.g. `selected` in `<option selected />`) are
 * expected to have an empty string as value (which is what the chrome devtools protocol returns).
 */
internal fun List<String>.asDOMAttributes(): DOMAttributes {
    val list = this
    val attrsMap = buildMap<String, String> {
        for (i in indices step 2) {
            put(list[i], list[i+1])
        }
    }
    return DOMAttributes(attrsMap)
}

/**
 * Represents [HTML attributes](https://www.w3schools.com/tags/ref_attributes.asp) of a DOM element.
 */
class DOMAttributes(attributesMap: Map<String, String>): Map<String, String> by attributesMap {

    /**
     * Specifies where to send the form-data when a form is submitted.
     *
     * Belongs to: `<form>`
     */
    val action: String? by attribute()

    /**
     * Specifies an alternate text when the original element fails to display.
     *
     * Belongs to: `<area>`, `<img>`, `<input>`
     */
    val alt: String? by attribute()

    /**
     * Specifies that the script is executed asynchronously (only for external scripts).
     *
     * Belongs to: `<script>`
     */
    val async: Boolean? by booleanAttribute()

    /**
     * Specifies whether the <form> or the <input> element should have autocomplete enabled
     *
     * Belongs to: `<form>`, `<input>`
     */
    val autocomplete: Boolean? by attribute {
        when (it) {
            "on" -> true
            "off" -> false
            else -> error("unknown value '$it' for autocomplete attribute")
        }
    }

    /**
     * Specifies that the element should automatically get focus when the page loads.
     *
     * Belongs to: `<button>`, `<input>`, `<select>`, `<textarea>`
     */
    val autofocus: Boolean? by booleanAttribute()

    /**
     * Specifies that the audio/video will start playing as soon as it is ready.
     *
     * Belongs to: `<audio>`, `<video>`
     */
    val autoplay: Boolean? by booleanAttribute()

    /**
     * Specifies the character encoding.
     *
     * Belongs to: `<meta>`, `<script>`
     */
    val charset: Charset? by attribute { Charset.forName(it) }

    /**
     * Specifies that an <input> element should be pre-selected when the page loads (for type="checkbox" or
     * type="radio").
     *
     * Belongs to: `<input>`
     */
    val checked: Boolean? by booleanAttribute()

    /**
     * Specifies one or more classnames for an element (refers to a class in a style sheet)
     */
    val `class`: String? by attribute("class")

    /**
     * Specifies one or more classnames for an element (refers to a class in a style sheet)
     */
    val classes: List<String> by attribute("class") {
        it?.split(" ") ?: emptyList()
    }

    /**
     * Specifies the visible width of a text area.
     *
     * Belongs to: `<textarea>`
     */
    val cols: Int? by intAttribute()

    /**
     * Specifies the URL of the resource to be used by the object.
     *
     * Belongs to: `<object>`
     */
    val data: String? by attribute()

    /**
     * Specifies which form element(s) a label/calculation is bound to.
     *
     * Belongs to: `<label>`
     */
    val `for`: String? by attribute()

    /**
     * Specifies the name of the form the element belongs to.
     *
     * Belongs to: `<button>`, `<fieldset>`, `<input>`, `<label>`, `<meter>`, `<object>`, `<output>`, `<select>`,
     * `<textarea>`
     */
    val form: String? by attribute()

    /**
     * Specifies the height of the element.
     *
     * Belongs to: `<canvas>`, `<embed>`, `<iframe>`, `<img>`, `<input>`, `<object>`, `<video>`
     */
    val height: Int? by intAttribute()

    /**
     * Specifies the URL of the page the link goes to.
     *
     * Belongs to: `<a>`, `<area>`, `<base>`, `<link>`
     */
    val href: String? by attribute()

    /**
     * Specifies a unique id for an element.
     */
    val id: String? by attribute()

    /**
     * Specifies the language of the element's content.
     */
    val lang: String? by attribute()

    /**
     * Specifies the HTTP method to use when sending form-data.
     *
     * Belongs to: `<form>`
     */
    val method: String? by attribute()

    /**
     * Specifies the name of the element.
     *
     * Belongs to: `<button>`, `<fieldset>`, `<form>`, `<iframe>`, `<input>`, `<map>`, `<meta>`, `<object>`,
     * `<output>`, `<param>`, `<select>`, `<textarea>`
     */
    val name: String? by attribute()

    /**
     * Specifies a regular expression that an `<input>` element's value is checked against.
     *
     * Belongs to: `<input>`
     */
    val pattern: String? by attribute()

    /**
     * Specifies a short hint that describes the expected value of the element.
     *
     * Belongs to: `<input>`, `<textarea>`
     */
    val placeholder: String? by attribute()

    /**
     * Specifies the visible number of lines in a text area.
     *
     * Belongs to: `<textarea>`
     */
    val rows: Int? by intAttribute()

    /**
     * Specifies the number of rows a table cell should span.
     *
     * Belongs to: `<td>`, `<th>`
     */
    val rowspan: Int? by intAttribute()

    /**
     * Specifies that an option should be pre-selected when the page loads.
     *
     * Belongs to: `<option>`
     */
    val selected: Boolean? by booleanAttribute()

    /**
     * Specifies the width, in characters (for <input>) or specifies the number of visible options (for <select>).
     *
     * Belongs to: `<input>`, `<select>`
     */
    val inputSize: Int? by intAttribute("size")

    /**
     * Specifies that the element is read-only.
     *
     * Belongs to: `<input>`, `<textarea>`
     */
    val readonly: Boolean? by booleanAttribute()

    /**
     * Specifies the relationship between the current document and the linked document.
     *
     * Belongs to: `<a>`, `<area>`, `<form>`, `<link>`
     */
    val rel: String? by attribute()

    /**
     * Specifies that the element must be filled out before submitting the form.
     *
     * Belongs to: `<input>`, `<select>`, `<textarea>`
     */
    val required: Boolean? by booleanAttribute()

    /**
     * Specifies the URL of the media file.
     *
     * Belongs to: `<audio>`, `<embed>`, `<iframe>`, `<img>`, `<input>`, `<script>`, `<source>`, `<track>`, `<video>`
     */
    val src: String? by attribute()

    /**
     * Specifies an inline CSS style for an element.
     */
    val style: String? by attribute()

    /**
     * Specifies extra information about an element.
     */
    val title: String? by attribute()

    /**
     * Specifies the type of element.
     *
     * Belongs to: `<a>`, `<button>`, `<embed>`, `<input>`, `<link>`, `<menu>`, `<object>`, `<script>`, `<source>`, `<style>`
     */
    val type: String? by attribute()

    /**
     * Specifies the value of the element.
     *
     * Belongs to: `<button>`, `<input>`, `<li>`, `<option>`, `<meter>`, `<progress>`, `<param>`
     */
    val value: String? by attribute()

    /**
     * Specifies the width of the element.
     *
     * Belongs to: `<canvas>`, `<embed>`, `<iframe>`, `<img>`, `<input>`, `<object>`, `<video>`
     */
    val width: Int? by intAttribute()
}

private fun attribute(name: String? = null) = attribute(name) { it }

private fun intAttribute(name: String? = null) = attribute(name) { it?.toInt() }

private fun booleanAttribute(name: String? = null) = attribute(name) {
    // Attributes without value (e.g. "selected" in <option selected />) are returned as empty strings by the protocol.
    // For booleans, this counts as "true"
    when (it) {
        null -> null
        "" -> true
        else -> it.toBoolean()
    }
}

private inline fun <T> attribute(
    customName: String? = null,
    crossinline transform: (String?) -> T,
): DOMAttributesDelegate<T> = DOMAttributesDelegate(customName) { value, _ -> value.let(transform) }

private class DOMAttributesDelegate<T>(
    private val customName: String? = null,
    private val transform: (String?, String) -> T,
) : ReadOnlyProperty<DOMAttributes, T> {
    override operator fun getValue(thisRef: DOMAttributes, property: KProperty<*>): T {
        val attributeName = customName ?: property.name
        return transform(thisRef[attributeName], attributeName)
    }
}
