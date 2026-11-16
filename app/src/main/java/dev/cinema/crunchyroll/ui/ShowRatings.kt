package dev.cinema.crunchyroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.design.*

@Composable
internal fun ShowRatingsSection(ratings: ShowRatings,demo: Boolean,onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("show-ratings"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Ratings",fontSize=22.sp,fontWeight=FontWeight.Bold)
        if(demo) Text("Sample ratings · Demo data",color=Muted,fontSize=12.sp)
        val fontScale=LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=((maxWidth+10.dp)/(104.dp*fontScale+10.dp)).toInt().coerceIn(1,3)
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                RatingSource.entries.chunked(columns).forEach {sources ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        sources.forEach {source -> RatingCard(source,ratings[source],Modifier.weight(1f))}
                        repeat(columns-sources.size) {Spacer(Modifier.weight(1f))}
                    }
                }
            }
        }
        if(ratings.values.values.any {it==RatingState.Error}) Action("Retry ratings",Glyph.Refresh,onClick=onRetry)
        if(!demo) Text("MyAnimeList via Tenrai · IMDb via Cinemeta. MyAnimeList rank is for the matched anime entry.",color=Muted,fontSize=12.sp,lineHeight=18.sp)
    }
}

@Composable
private fun RatingCard(source: RatingSource,state: RatingState,modifier: Modifier) {
    val rating=(state as? RatingState.Available)?.rating
    val accent=when(source) {
        RatingSource.Crunchyroll -> Orange
        RatingSource.MyAnimeList -> Color(0xFF8BACED)
        RatingSource.Imdb -> Color(0xFFF5C518)
    }
    Column(modifier.semantics(mergeDescendants=true) {}.testTag("rating-${source.name}")
        .background(Surface,RoundedCornerShape(12.dp)).border(1.dp,Divider,RoundedCornerShape(12.dp))
        .padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(source.label,color=accent,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.heightIn(min=32.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            if(state==RatingState.Loading) CircularProgressIndicator(Modifier.size(18.dp),color=accent,strokeWidth=2.dp)
            else {
                Text(rating?.formatted(source) ?: "—",fontSize=24.sp,fontWeight=FontWeight.Bold)
                if(rating?.score!=null) Text("/${source.maximum}",color=Muted,fontSize=12.sp)
            }
        }
        Text(when(state) {
            RatingState.Loading -> "Loading…"
            RatingState.Error -> "Couldn’t load"
            RatingState.Unavailable -> "Unavailable"
            is RatingState.Available -> if(source==RatingSource.MyAnimeList) rating?.rank?.let {"Rank #$it"} ?: "Unranked" else "Audience score"
        },color=Muted,fontSize=11.sp)
    }
}
