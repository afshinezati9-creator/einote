# eiNote — Product Definition

## Vision
eiNote should feel like a quiet digital notebook rather than a collection of separate apps.

A user should be able to write, check something off, record a thought, attach a photo, record an expense, plan an activity, find old information, and protect or back up their data.

## Core UX rule
**Progressive disclosure:** show the most important actions first and reveal advanced actions only when needed.

The interface must remain calm even as functionality grows.

## Information model
The primary object is an **Entry**. An entry can contain a title, text, checklist items, tags, dates, attachments, audio, financial data, reminders, and pin/archive state.

Specialized views can present the same underlying data as Notes, Tasks, Planner, Finance, Important, Media, and Search.

## Offline-first
The app must remain usable without a network: create/edit/delete, search, attachments, audio, finance records, reminders, backup and restore.

Network access, if introduced later, must be optional.

## Customization
- light/dark/system theme
- accent color
- font size
- compact/comfortable density
- home-screen modules
- default note type
- backup behavior
- security preferences

## Non-goals
The first versions should not become a social network, cloud-dependent collaboration suite, overloaded project-management system, or advertising platform.
