package com.aegida.app.ui

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.aegida.app.R
import com.aegida.core.model.SafetyPicture

/** The drawing and tile colour for each safety picture (both generated from the storyboard by scripts/make-safety-pictures.py). */
object Pictures {
    @DrawableRes
    fun drawable(picture: SafetyPicture): Int = when (picture) {
        SafetyPicture.Sunflower -> R.drawable.picture_sunflower
        SafetyPicture.Owl -> R.drawable.picture_owl
        SafetyPicture.Teapot -> R.drawable.picture_teapot
        SafetyPicture.Sailboat -> R.drawable.picture_sailboat
        SafetyPicture.Apple -> R.drawable.picture_apple
        SafetyPicture.Butterfly -> R.drawable.picture_butterfly
        SafetyPicture.House -> R.drawable.picture_house
        SafetyPicture.Star -> R.drawable.picture_star
        SafetyPicture.Fish -> R.drawable.picture_fish
        SafetyPicture.Tree -> R.drawable.picture_tree
    }

    @ColorRes
    fun tileColor(picture: SafetyPicture): Int = when (picture) {
        SafetyPicture.Sunflower -> R.color.picture_tile_sunflower
        SafetyPicture.Owl -> R.color.picture_tile_owl
        SafetyPicture.Teapot -> R.color.picture_tile_teapot
        SafetyPicture.Sailboat -> R.color.picture_tile_sailboat
        SafetyPicture.Apple -> R.color.picture_tile_apple
        SafetyPicture.Butterfly -> R.color.picture_tile_butterfly
        SafetyPicture.House -> R.color.picture_tile_house
        SafetyPicture.Star -> R.color.picture_tile_star
        SafetyPicture.Fish -> R.color.picture_tile_fish
        SafetyPicture.Tree -> R.color.picture_tile_tree
    }
}
