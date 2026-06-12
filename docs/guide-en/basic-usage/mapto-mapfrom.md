# @MapTo and @MapFrom

Both annotations declare the same thing — "generate a mapping between these two classes" —
they only differ in **which class carries the declaration**.

## @MapTo — declared on the source

```kotlin
@MapTo(User::class)
data class UserResponse(val id: Long, val name: String)

// generates: fun UserResponse.toUserResult(): Result<User>
```

## @MapFrom — declared on the target

Sometimes the source class isn't yours to annotate (another module, generated code). Declare
on the target instead; the generated function is identical:

```kotlin
@MapFrom(UserResponse::class)
data class User(val id: Long, val name: String)

// generates the same: fun UserResponse.toUserResult(): Result<User>
```

## Which side should you annotate?

Prefer **`@MapTo` on the wire model**. Wire models are the volatile side — when the API
changes, the model and its mapping rules change in the same file. Reach for `@MapFrom` when
the source class is outside your control.

## Both are repeatable

One source can map to several targets, and one target can accept several sources:

```kotlin
@MapTo(User::class)
@MapTo(UserListItem::class)
data class UserResponse(val id: Long, val name: String, val avatarUrl: String?)

// fun UserResponse.toUserResult(): Result<User>
// fun UserResponse.toUserListItemResult(): Result<UserListItem>
```

Field-level directives can be scoped to one of the targets where it matters — see
[`@FieldMap(targetClass = …)`](field-mapping.md).

## What gets matched

Fields are matched **by name** between the source's properties and the target's primary
constructor parameters. For each matched pair, in order:

1. same type → copied
2. different type → a [converter](../type-conversion/built-in.md) is resolved at compile time
3. nested `@MapTo`/`@MapFrom` pair → routed through the generated sub-mapper
4. nothing fits → **compile error** naming the field and the missing piece

Unmatched *target* fields with no default become required parameters of the generated
function (see [caller-supplied parameters](field-mapping.md#caller-supplied-parameters)).

> Next: **[Field Mapping and the Ignore Family →](field-mapping.md)**
