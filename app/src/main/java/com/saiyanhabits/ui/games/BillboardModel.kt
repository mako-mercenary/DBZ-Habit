package com.saiyanhabits.ui.games

import android.graphics.BitmapFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Makes a 3D model of a flat picture: one upright quad carrying [png], standing
 * on y = 0, centred in x, facing +z. It is unlit, so the picture keeps its own
 * colours, and see-through where the picture is, so cut-outs have clean edges.
 *
 * The size in metres comes from [width] or [height] (whichever is given) and
 * the picture's proportions. Returns a GLB in a direct buffer, ready for
 * Filament, or null if [png] cannot be read as an image.
 */
fun billboardGlb(png: ByteArray, width: Float? = null, height: Float? = null): ByteBuffer? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val aspect = bounds.outWidth.toFloat() / bounds.outHeight
    val w = width ?: (height!! * aspect)
    val h = height ?: (width!! / aspect)
    return billboardGlb(png, w, h, mimeType = "image/png")
}

/** The GLB itself, for a picture already known to be [w] by [h] metres. Kept apart so it can be tested. */
internal fun billboardGlb(image: ByteArray, w: Float, h: Float, mimeType: String): ByteBuffer {
    val x = w / 2
    val positions = floatArrayOf(-x, 0f, 0f, x, 0f, 0f, x, h, 0f, -x, h, 0f)
    val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
    // glTF puts the texture's origin at the top left.
    val uvs = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
    val indices = shortArrayOf(0, 1, 2, 0, 2, 3)

    val posAt = 0
    val norAt = posAt + positions.size * 4
    val uvAt = norAt + normals.size * 4
    val idxAt = uvAt + uvs.size * 4
    val imgAt = pad4(idxAt + indices.size * 2)
    val binLength = pad4(imgAt + image.size)
    val bin = ByteBuffer.allocate(binLength).order(ByteOrder.LITTLE_ENDIAN)
    positions.forEach { bin.putFloat(it) }
    normals.forEach { bin.putFloat(it) }
    uvs.forEach { bin.putFloat(it) }
    indices.forEach { bin.putShort(it) }
    bin.position(imgAt)
    bin.put(image)

    val json = """
        {"asset":{"version":"2.0","generator":"Saiyan Habits"},
         "extensionsUsed":["KHR_materials_unlit"],
         "scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
         "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3,"material":0}]}],
         "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"metallicFactor":0,"roughnessFactor":1},
           "alphaMode":"MASK","alphaCutoff":0.5,"doubleSided":true,"extensions":{"KHR_materials_unlit":{}}}],
         "textures":[{"source":0,"sampler":0}],
         "samplers":[{"magFilter":9729,"minFilter":9987,"wrapS":33071,"wrapT":33071}],
         "images":[{"bufferView":4,"mimeType":"$mimeType"}],
         "accessors":[
           {"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[${-x},0,0],"max":[$x,$h,0]},
           {"bufferView":1,"componentType":5126,"count":4,"type":"VEC3"},
           {"bufferView":2,"componentType":5126,"count":4,"type":"VEC2"},
           {"bufferView":3,"componentType":5123,"count":6,"type":"SCALAR"}],
         "bufferViews":[
           {"buffer":0,"byteOffset":$posAt,"byteLength":${positions.size * 4},"target":34962},
           {"buffer":0,"byteOffset":$norAt,"byteLength":${normals.size * 4},"target":34962},
           {"buffer":0,"byteOffset":$uvAt,"byteLength":${uvs.size * 4},"target":34962},
           {"buffer":0,"byteOffset":$idxAt,"byteLength":${indices.size * 2},"target":34963},
           {"buffer":0,"byteOffset":$imgAt,"byteLength":${image.size}}],
         "buffers":[{"byteLength":$binLength}]}
    """.trimIndent().replace("\n", "").toByteArray(Charsets.UTF_8)
    val jsonLength = pad4(json.size)

    val total = 12 + 8 + jsonLength + 8 + binLength
    val glb = ByteBuffer.allocateDirect(total).order(ByteOrder.LITTLE_ENDIAN)
    glb.putInt(0x46546C67).putInt(2).putInt(total)
    glb.putInt(jsonLength).putInt(0x4E4F534A).put(json)
    repeat(jsonLength - json.size) { glb.put(' '.code.toByte()) }
    glb.putInt(binLength).putInt(0x004E4942).put(bin.array())
    glb.flip()
    return glb
}

private fun pad4(n: Int) = (n + 3) / 4 * 4
