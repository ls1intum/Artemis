function g = finalGrade(grades,weights)
    g = round((grades * weights.').',1);
end
