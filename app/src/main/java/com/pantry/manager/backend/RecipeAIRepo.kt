package com.pantry.manager.backend

import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.common.FeatureStatus
import java.lang.IllegalStateException
import android.content.Context
import com.pantry.manager.data.db.AppDatabase
import kotlinx.coroutines.flow.first


class RecipeAIRepo (context: Context){
    private val database = AppDatabase.getDatabase(context)
    private val model: GenerativeModel = Generation.getClient()

    private suspend fun getIngredients(): List<String> {
        val ingredients = database.ingredientDao().getAllIngredients().first()
        return ingredients.map {it.name}
    }

    suspend fun isAvailable(): Boolean{
        return model.checkStatus() == FeatureStatus.AVAILABLE
    }

    suspend fun suggestRecipe(): String {
        val ingredients = getIngredients()
        require(ingredients.isNotEmpty()){
            "Add at least one ingredient to system or select generate a recipe without ingrediants"
        }

        val prompt = """
            You will assist in generating a recipe
            
            Here are the Availiable ingredient: ${ingredients.joinToString(", ")}
            Create one short recipe using the availiable ingrediants
            
            Rules:
            1. Only use ingredients listed above.
            2. Return the recipe name on the first line
            3. Return the ingredients on the second line seperated by commas
            4. Return instructions on the remianing lines
            3. Dont claim the recipe is allergy-safe.
            4. Keep the instructions to 3-6 short steps.
        """.trimIndent()

        val res = model.generateContent(prompt)

        return res.candidates.firstOrNull()?.text
            ?: throw IllegalStateException(
                "AI did not return recipie."
            )

    }

    fun close(){
        model.close()
    }
}