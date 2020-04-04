package com.bbirds.covidtracker

// standard java Base64.getEncoder() forces to use more recent API

object Base64 {

    private const val base64chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(string: String): String {
        var s = string
        var r = ""
        var p = ""

        var c = s.length % 3
        if (c > 0) {
            while (c < 3) {
                p += "="
                s += "\u0000"
                c++
            }
        }

        c = 0
        while (c < s.length) {
            if (c > 0 && c / 3 * 4 % 76 == 0) r += "\r\n"
            val n = ((s[c].toInt() shl 16) + (s[c + 1].toInt() shl 8)
                    + s[c + 2].toInt())
            val n1 = n shr 18 and 63
            val n2 = n shr 12 and 63
            val n3 = n shr 6 and 63
            val n4 = n and 63
            r += ("" + base64chars[n1] + base64chars[n2]
                    + base64chars[n3] + base64chars[n4])
            c += 3
        }
        return r.substring(0, r.length - p.length) + p
    }

}