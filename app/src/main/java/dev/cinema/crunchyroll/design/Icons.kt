package dev.cinema.crunchyroll.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

enum class Glyph { Home, Search, List, Profile, Play, Pause, Plus, Check, Back, Close, Settings, History, Chevron, Rewind, Forward, Tracks, Refresh, Info, More }

@Composable
fun CinemaIcon(glyph: Glyph, tint: Color = Paper, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x,y), Offset(x2,y2), 1.8f, StrokeCap.Round)
            fun path(vararg points: Float) {
                val p = Path().apply { moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i+1]) }
                drawPath(p, tint, style = stroke)
            }
            when (glyph) {
                Glyph.More -> for (y in listOf(5f, 12f, 19f)) drawCircle(tint, 1.8f, Offset(12f, y))
                Glyph.Home -> { path(3f,10f,12f,3f,21f,10f); path(5f,9f,5f,21f,10f,21f,10f,14f,14f,14f,14f,21f,19f,21f,19f,9f) }
                Glyph.Search -> { drawCircle(tint,7f,Offset(10f,10f),style=stroke);line(15f,15f,21f,21f) }
                Glyph.List -> { path(6f,3f,18f,3f,18f,21f,12f,17f,6f,21f,6f,3f) }
                Glyph.Profile -> { drawCircle(tint,4f,Offset(12f,7f),style=stroke);drawArc(tint,180f,180f,false,Offset(4f,14f),androidx.compose.ui.geometry.Size(16f,14f),style=stroke) }
                Glyph.Play -> drawPath(Path().apply { moveTo(7f,4f);lineTo(21f,12f);lineTo(7f,20f);close() },tint)
                Glyph.Pause -> { line(8f,5f,8f,19f);line(16f,5f,16f,19f) }
                Glyph.Plus -> { line(12f,5f,12f,19f);line(5f,12f,19f,12f) }
                Glyph.Check -> path(4f,12f,10f,18f,21f,6f)
                Glyph.Back -> { path(11f,4f,3f,12f,11f,20f);line(3f,12f,21f,12f) }
                Glyph.Close -> {line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
                Glyph.Chevron -> path(9f,5f,16f,12f,9f,19f)
                Glyph.Settings -> {
                    val gear=Path().apply {
                        repeat(32) {i ->
                            val angle=(i*Math.PI/16)-Math.PI/2
                            val radius=if(i%4==1 || i%4==2) 10f else 8f
                            val x=12f+radius*kotlin.math.cos(angle).toFloat()
                            val y=12f+radius*kotlin.math.sin(angle).toFloat()
                            if(i==0) moveTo(x,y) else lineTo(x,y)
                        }
                        close()
                    }
                    drawPath(gear,tint,style=stroke)
                    drawCircle(tint,3f,Offset(12f,12f),style=stroke)
                }
                Glyph.History, Glyph.Refresh -> {drawArc(tint,215f,300f,false,Offset(3f,3f),androidx.compose.ui.geometry.Size(18f,18f),style=stroke);path(2f,3f,2f,9f,8f,9f); if(glyph==Glyph.History) path(12f,7f,12f,12f,16f,14f)}
                Glyph.Rewind -> {path(11f,5f,3f,12f,11f,19f);path(21f,5f,13f,12f,21f,19f)}
                Glyph.Forward -> {path(3f,5f,11f,12f,3f,19f);path(13f,5f,21f,12f,13f,19f)}
                Glyph.Tracks -> {path(3f,4f,21f,4f,21f,19f,3f,19f,3f,4f);line(6f,10f,10f,10f);line(14f,10f,18f,10f);line(6f,14f,18f,14f)}
                Glyph.Info -> {drawCircle(tint,9f,Offset(12f,12f),style=stroke);line(12f,11f,12f,17f);drawCircle(tint,1f,Offset(12f,7f))}
            }
        }
    }
}
