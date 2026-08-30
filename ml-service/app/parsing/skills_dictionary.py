"""Flat keyword list for deterministic skill detection.

Deliberately simple - a word-boundary substring match against this list,
nothing smarter. This is intentional per the plan: this week's parser is
the cheap/fast/no-cost pass, and imperfect skill recall here is fine
because Week 4 adds an LLM pass specifically to catch what this misses.

Grow this list as needed; it's plain data, no logic depends on its size
or order.
"""

SKILLS = [
    # Languages
    "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C++", "C#",
    "Kotlin", "Scala", "Ruby", "PHP", "Swift",
    # Backend frameworks
    "Spring Boot", "Spring", "FastAPI", "Django", "Flask", "Express",
    "Node.js", ".NET",
    # Frontend
    "React", "Next.js", "Vue", "Angular", "Tailwind CSS", "Tailwind",
    # Data / infra
    "PostgreSQL", "MySQL", "MongoDB", "Redis", "Kafka", "RabbitMQ",
    "Elasticsearch", "pgvector",
    # DevOps
    "Docker", "Kubernetes", "Terraform", "AWS", "GCP", "Azure",
    "GitHub Actions", "CI/CD", "Jenkins",
    # ML / data
    "Machine Learning", "Deep Learning", "PyTorch", "TensorFlow",
    "scikit-learn", "pandas", "NumPy",
    # Practices
    "REST", "GraphQL", "gRPC", "Microservices", "Agile", "Scrum",
    "Test-Driven Development", "TDD",
]
