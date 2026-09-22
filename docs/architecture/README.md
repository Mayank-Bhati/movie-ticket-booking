# Architecture

This project is a feature-oriented modular monolith. See the completed architecture package for
system context, module boundaries, runtime flows, the data model, and architecture decisions.

The central invariant is:

> For a showing and physical seat, at most one active hold or confirmed booking may own the
> corresponding inventory row.

