package com.sahsenvar.kmapper.validation.builtin

import com.sahsenvar.kmapper.validation.Validator

object NotEmptyCollectionValidator : Validator<Collection<*>>(Collection::class) {
    override fun validate(value: Collection<*>): String? =
        if (value.isEmpty()) "must not be empty" else null
}
