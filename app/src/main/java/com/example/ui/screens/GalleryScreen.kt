                                                        Text(
                                                            "Group by: $option",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        if (isSelected) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setGalleryGroupBy(option)
                                                        groupMenuVisible = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Rich Search Suggestions & Tokens Panel (when search is open)
                        AnimatedVisibility(
                            visible = uiState.gallerySearchActive,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            RichGallerySearchPanel(
                                uiState = uiState,