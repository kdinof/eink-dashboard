package com.eink.dashboard.modules.weather.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.eink.dashboard.ui.ink.InkIcons

/** Maps a WMO bucket onto the closest Ink UI line icon. */
val WmoCondition.icon: ImageVector
    get() = when (this) {
        WmoCondition.CLEAR, WmoCondition.MAINLY_CLEAR -> InkIcons.Sun
        WmoCondition.PARTLY_CLOUDY, WmoCondition.OVERCAST, WmoCondition.FOG -> InkIcons.Cloud
        WmoCondition.DRIZZLE, WmoCondition.RAIN, WmoCondition.FREEZING_RAIN,
        WmoCondition.SHOWERS, WmoCondition.THUNDERSTORM -> InkIcons.Rain
        WmoCondition.SNOW, WmoCondition.SNOW_SHOWERS -> InkIcons.Snow
        WmoCondition.UNKNOWN -> InkIcons.Cloud
    }
